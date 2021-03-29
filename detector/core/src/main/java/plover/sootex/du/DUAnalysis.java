package plover.sootex.du;

import java.util.*;

import plover.soot.CollectionUtils;
import plover.soot.SootUtils;
import plover.soot.Utils;
import plover.sootex.location.AccessPath;
import plover.sootex.location.Location;
import plover.sootex.sideeffect.ISideEffectAnalysis;
import plover.soot.hammock.CFGEntry;
import plover.soot.hammock.CFGExit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.IdentityStmt;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.*;

/**
 *  A reaching DEF or USE analysis
 */
public abstract class DUAnalysis extends ForwardFlowAnalysis<Unit, FlowSet> implements IReachingDUQuery{

	public static final Logger LOGGER = LoggerFactory.getLogger(DUAnalysis.class);

	protected SootMethod _method;
	
	protected ISideEffectAnalysis _sideEffect;
	protected FlowSet _fullSet;
	/**
	 * Generated DEF/USE for each <code>Unit</code>
	 */
	protected Map<Unit,Object> _unit2Gen;
	private Map<Unit, FlowSet> _unit2KillSet;
	
	public DUAnalysis(MethodOrMethodContext mc, DirectedGraph<Unit> graph, ISideEffectAnalysis sideEffect){
		super(graph);
		
		this._method = mc.method();	   
	    this._sideEffect = sideEffect;
	} 
	
	/** ID name of the analysis  */
	protected abstract String getAnalysisName();
	
	/** DEF/USE set at the method entry. */
	protected abstract Collection<ReachingDU> getEntryDU();
	
	/** Collect DU of each statement */
	protected abstract Collection<ReachingDU> collectStmtDU(Unit stmt);
	
	private static class FinalizedFlowSet extends ArraySparseSet {
		public FinalizedFlowSet(FlowSet set){
			maxElements = numElements = set.size();
		    elements = new Object[numElements];
		      
		    int i=0;    
			for(Iterator<?> it = set.iterator();it.hasNext(); i++){
				elements[i] = it.next();
			}
		}
		
		@SuppressWarnings("rawtypes")
		@Override
		public final List toList(){
		    return Arrays.asList(elements);
		}
	}
	
	protected void finalizeFlowSets(){
		for(Map.Entry<Unit, FlowSet> e: this.unitToBeforeFlow.entrySet()){
			FlowSet v = e.getValue();
			FlowSet vf = new FinalizedFlowSet(v);
			e.setValue(vf);
		}
	}
	
	
	public void build(){
		Date startTime = new Date();
		
		FlowUniverse<ReachingDU> universe = collectUniverseFlowSet();
		initKillSet(universe);
		universe = null;
		
		doAnalysis();
		finalizeFlowSets();
		clean();
		
		Date endTime=new Date();	

		LOGGER.debug("{} Analyze {} -- in {}", getAnalysisName(), _method, Utils.getTimeConsumed(startTime,endTime));
	} 
	
	/** clean temporal data */
	protected void clean(){		
		unitToAfterFlow = null;
		filterUnitToAfterFlow = null;
		filterUnitToBeforeFlow = null;
		graph = null;
		_sideEffect = null;		
		_unit2KillSet = null; 
	}

	/**
	 * Get the DEF/USE of <code>Unit u</code>
	 * @param u target unit
	 * @return
	 */
	public Collection<Location> getDULocations(Unit u){
		FlowSet gen = (FlowSet)_unit2Gen.get(u);
		if(gen==null){
			throw new RuntimeException("Statement " + SootUtils.getStmtString(u)+" may not in the CFG of " + _method);
		}
		
		Collection<Location> duLocations = new HashSet<Location>();
		for(Iterator<?> it=gen.iterator(); it.hasNext(); ){
			ReachingDU rdu = (ReachingDU)it.next();
			duLocations.addAll(rdu.getLocations());
		}		
		return duLocations;
	}
	
	public Collection<AccessPath> getDUAccessPath(Unit u) {
		FlowSet gen = (FlowSet)_unit2Gen.get(u);
		if(gen==null){
			throw new RuntimeException("Statement " + SootUtils.getStmtString(u)+" may not in the CFG of " + _method);
		}

		Collection<AccessPath> duAccessPaths = new HashSet<AccessPath>();
		for(Iterator<?> it=gen.iterator(); it.hasNext(); ){
			ReachingDU rdu = (ReachingDU)it.next();
			duAccessPaths.add(rdu.getAccessPath());
		}
		return duAccessPaths;
	}

	/**
	 * Get the location killed by a statement.
	 */
	protected AccessPath getKilledAccessPath(Unit u){
		if(u == CFGEntry.v() || u == CFGExit.v() ||
		   u instanceof IdentityStmt || !(u instanceof Stmt))
			return null;
				
		Stmt s = (Stmt)u;
		if(s.getDefBoxes().size()!=1){
			return null;
		}
		
		Value defValue = ((ValueBox)s.getDefBoxes().iterator().next()).getValue();
		if(defValue instanceof Local || defValue instanceof StaticFieldRef){
			AccessPath def = AccessPath.valueToAccessPath(null, s, defValue);
			return def;
		}

		return null;
	}
	
	private void initKillSet(FlowUniverse<ReachingDU> universe){
		 _unit2KillSet=new HashMap<Unit, FlowSet>(graph.size()*2+1,0.7f);
		for(Iterator<Unit> it = graph.iterator(); it.hasNext();){
			Unit stmt = it.next();
			FlowSet killedSet = _fullSet.clone();
			_unit2KillSet.put(stmt,killedSet);	
			
			AccessPath killedAp = getKilledAccessPath(stmt);			
			if(killedAp == null){
				continue;
			}
			
			for (Iterator<?> duIt = universe.iterator(); duIt.hasNext();) {
				ReachingDU rd = (ReachingDU) duIt.next();
				if (rd.getAccessPath() != null) {
					if (rd.getAccessPath() == killedAp)
						killedSet.add(rd);
				}
				for (Location loc : rd.getLocations()) {
					if (loc.equals(killedAp.getRoot())) {
						killedSet.add(rd);
						break;
					}
				}
			}
		}
	}
	
	/**
	 * Get the universe reaching DEF/USE set.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected FlowUniverse<ReachingDU> collectUniverseFlowSet(){
        // collect the def/use of each statement
        _unit2Gen = new HashMap<Unit,Object>(graph.size()*2+1,0.7f);
        
        List<ReachingDU> allDU = new ArrayList<ReachingDU>();
        for(Iterator<?> it=graph.iterator();it.hasNext();){
        	Unit s = (Unit)it.next();
        	Collection<ReachingDU> duSet = collectStmtDU(s);
        	allDU.addAll(duSet);
        	_unit2Gen.put(s, duSet);
        }
        
        // build the empty set
        FlowUniverse<ReachingDU> universe = new ArrayFlowUniverse(allDU.toArray());
        // TODO XXX: new ArrayPackedSet(FlowUniverse) consume large memory, use clone() instead
       	_fullSet = new ArrayPackedSet(universe);
		
		// change the sparse representation to packed representation
	    for(Iterator<?> it=graph.iterator();it.hasNext();){
	        Unit s=(Unit)it.next();
	        Collection<ReachingDU> fset = (Collection<ReachingDU>)_unit2Gen.get(s);
	        FlowSet packedSet = _fullSet.clone();
	        for(ReachingDU d: fset){
	        	packedSet.add(d);
	        }
	        _unit2Gen.put(s,packedSet);
	    }
	    
	    return universe;
	}
	
	 ////////////////////// ForwardFlowAnalysis ///////////////////////////
	protected FlowSet newInitialFlow() {
		return _fullSet.clone();
	}

	protected FlowSet entryInitialFlow(){
	    return _fullSet.clone();
	}

	@Override
	protected void merge(FlowSet in1, FlowSet in2, FlowSet out) {
		in1.union(in2, out);
	}

	@Override
	protected void copy(FlowSet source, FlowSet dest) {
		source.copy(dest);
	}	

	@Override
    protected void flowThrough(FlowSet in, Unit s, FlowSet out) {
        FlowSet gen = (FlowSet)_unit2Gen.get(s);
        FlowSet kill = _unit2KillSet.get(s);
        in.difference(kill,out);        
		out.union(gen);
	}
	
	/////////////////////// IReachingDUQuery /////////////////////////////   
    public Collection<Unit> getReachingDUSites(Unit stmt, AccessPath ap, Location loc){
    	Set<Unit> froms = new HashSet<Unit>();
        FlowSet before = getFlowBefore(stmt);
        
        for(Iterator<?> it=before.iterator(); it.hasNext(); ){
            ReachingDU rd = (ReachingDU)it.next();
            Collection<Location> duLocs = rd.getLocations();
            if(duLocs.contains(loc)){
            	froms.add(rd.getStmt());
            }
        }
        
        return froms;
    }
    
    public Collection<Unit> getReachingDUSites(Unit stmt, AccessPath ap, Collection<Location> locs){
        Set<Unit> froms = new HashSet<Unit>();
        FlowSet before = getFlowBefore(stmt);
        findDUInFlowSet(locs,before.iterator(),froms);
        return froms;
    }
    
    /**
	 * Find the possible definitions to locations in collection <code>find</code>.
     * The result is added to 'froms' set
	 */
    private void findDUInFlowSet(Collection<Location> find, Iterator<?> flowSetIt, Set<Unit> froms){
    	if(find.isEmpty()){
    		return;
    	}
    	
        for(Iterator<?> it=flowSetIt; it.hasNext(); ){
            ReachingDU rd = (ReachingDU)it.next();
            Collection<Location> duLocs = rd.getLocations();
            
            boolean hasIntersection = false;
            if(duLocs instanceof List){
            	for(Location loc: duLocs){
            		if(find.contains(loc)){
            			hasIntersection = true;
            		}
            	}
            }
            else if(!(find instanceof Set)){
            	for(Location loc: find){
            		if(duLocs.contains(loc)){
            			hasIntersection = true;
            		}
            	}
            }
            //else if(duLocs instanceof SortedArraySet || find instanceof HashSet){            	
            //}
            else{
            	hasIntersection = CollectionUtils.hasInterset((Set<Location>)duLocs, (Set<Location>)find);
            }
            
            if(hasIntersection){
                froms.add(rd.getStmt());
            }
        }
    }
    
	
    /**
	 * Collect parameter locations, including the formals, this pointer.
	 */
	protected Collection<Location> collectParams(){
		Set<Location> locs = new HashSet<Location>();

		Body body = _method.getActiveBody();
		int argNum = _method.getParameterCount();
	    for(int i=0;i<argNum;i++){
	    	Local p = body.getParameterLocal(i);
	    	Location param = Location.valueToLocation(p);
	    	locs.add(param);
	    }
        
        if(!_method.isStatic()){
            locs.add(Location.getThisPointer(_method));
        }

        return locs;
	}
}
