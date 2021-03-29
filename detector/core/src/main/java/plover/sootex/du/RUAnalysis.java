package plover.sootex.du;

import java.util.*;

import plover.sootex.location.AccessPath;
import plover.sootex.location.HeapAbstraction;
import plover.sootex.location.Location;
import plover.sootex.ptsto.IPtsToQuery;
import plover.sootex.ptsto.PtsToHelper;
import plover.sootex.sideeffect.ISideEffectAnalysis;
import plover.soot.callgraph.Callees;
import plover.soot.hammock.CFGEntry;
import plover.soot.hammock.CFGExit;
import soot.*;
import soot.jimple.ArrayRef;
import soot.jimple.FieldRef;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.toolkits.graph.*;

/**
 *  A reaching use analysis (formally, upwards exposed usage) implemented with bit vectors.
 */
public class RUAnalysis extends DUAnalysis{
	protected IPtsToQuery _pt2Query;
	protected HeapAbstraction _heapAbstraction;
	
	public RUAnalysis(MethodOrMethodContext mc, DirectedGraph<Unit> graph, IPtsToQuery pt2Query,
					  ISideEffectAnalysis sideEffect){
		super(mc, graph, sideEffect);
		
		this._pt2Query = pt2Query;
	} 
	
	public void build(){
		super.build();
		_pt2Query = null;
	}
	
	/** ID name of the analysis  */
	protected String getAnalysisName(){
		return "RU";
	}
	
	/** USE set at the method entry. */
	protected Collection<ReachingDU> getEntryDU(){
		Collection<ReachingDU> entrySet = new ArrayList<ReachingDU>();
			
		// TODO: What should be the reaching uses at the method entry?
		/*Unit entry = CFGEntry.v();
		 * Collection<Location> params = collectParams(); for(Location loc:
		 * params){ AccessPath ap = AccessPath.getByRoot(loc); entrySet.add(new
		 * ReachingDU(entry,ap,loc)); }
		 * 
		 * Collection<Location> use = _sideEffect.getUseHeapLocs(_method);
		 * if(use.size()>0){ entrySet.add(new ReachingDU(entry,null,use)); }
		 */ 
		 
		return entrySet;
	}

	/**
	 * Collect (global/heap side-effect) USE for invocation statement <code>Unit invokeStmt</code>
	 * @param invokeStmt target invocation statement
	 * @return
	 */
	protected Collection<ReachingDU> collectInvokeUses(Unit invokeStmt){
		if (_sideEffect == null) {
			return new ArrayList<>();
		}

		Collection<ReachingDU> ruSet = new ArrayList<ReachingDU>(); 
	    CallGraph cg = Scene.v().getCallGraph();
	    Callees callees = new Callees(cg, invokeStmt);

		List<SootMethod> tgtMethods = new ArrayList<>();

		// collect definitions to heap location
		if(callees.all().size() >= 1) {
			tgtMethods.addAll(callees.all());
		} else if (((Stmt)invokeStmt).containsInvokeExpr()){
			// workaround for imprecision of call graph
			tgtMethods.add(((Stmt)invokeStmt).getInvokeExpr().getMethod());
		}


		for(SootMethod tgt: tgtMethods) {
			Collection<Location> calleeUses = new HashSet<>();
			if (tgt.isConcrete()){
				Collection<AccessPath> useAp = _sideEffect.getUseHeapLocs(tgt);
				if (useAp == null) {
					continue;
				}
				Collection<AccessPath> mappingUseAp = _sideEffect.getMappingAccessPath((Stmt) invokeStmt, tgt, useAp);
				for (AccessPath ap : mappingUseAp) {
					// TODO pay attention to the underlying implementation,
					//  it can be field sensitive point-to analysis or field insensitive point-to analysis
					calleeUses = (PtsToHelper.getAccessedLocations(_pt2Query, null, ap));
					ReachingDU use = new ReachingDU(invokeStmt, ap, calleeUses);
					ruSet.add(use);
				}
			}
			else{
				ruSet.addAll(getNativeCallUse(invokeStmt, tgt));
			}
		}
		
		return ruSet; 
	}

	/**
	 *
	 * @param u
	 * @param tgt
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Collection<ReachingDU> getNativeCallUse(Unit u, SootMethod tgt) {
		Collection<ReachingDU> ruSet = new ArrayList<ReachingDU>();

		InvokeExpr invoke = ((Stmt)u).getInvokeExpr();
		Value receiver = null;
		if (!invoke.getMethod().isStatic()) {
			InstanceInvokeExpr iie = (InstanceInvokeExpr)invoke;
			receiver = iie.getBase();
		}
		Collection<AccessPath> use = NativeMethodDUHelper.v().getUse(tgt, receiver, invoke.getArgs());
		if (use.size() > 0) {
			for(AccessPath d: use){
				// TODO pay attention to the underlying implementation,
				//  it can be field sensitive point-to analysis or field insensitive point-to analysis
				Collection<Location> useLocs = PtsToHelper.getAccessedLocations(_pt2Query, u, d);
				ReachingDU ru = new ReachingDU(u, d, useLocs);
				ruSet.add(ru);
			}
		}
		return ruSet;
	}

	/** Collect RU of each statement */
	protected Collection<ReachingDU> collectStmtDU(Unit stmt){
		if(stmt== CFGEntry.v()){
			return getEntryDU();
		}
		if(stmt== CFGExit.v() || stmt instanceof IdentityStmt){
			return Collections.emptyList();
		}  	
		
		Collection<ReachingDU> ruSet = new ArrayList<ReachingDU>();	
		
		Set<Value> uses = new HashSet<Value>();
		for(ValueBox box: stmt.getUseBoxes()){
			Value v = box.getValue();
			uses.add(v);
		}
		
		for(Value v: uses){
			if(v instanceof Local){
				Location root = Location.valueToLocation((Local)v);
				AccessPath ap = AccessPath.getByRoot(root);
				ReachingDU ru = new ReachingDU(stmt,ap,root);		        
		        ruSet.add(ru);
			}
			else if((v instanceof FieldRef) || (v instanceof ArrayRef)){
				AccessPath ap = AccessPath.valueToAccessPath(_method, stmt, v);
				// TODO pay attention to the underlying implementation,
				//  it can be field sensitive point-to analysis or field insensitive point-to analysis
				Collection<Location> duLocs = PtsToHelper.getAccessedLocations(_pt2Query, stmt, ap);
				ReachingDU ru = new ReachingDU(stmt,ap,duLocs);
		        ruSet.add(ru);
			}
			else if(v instanceof InvokeExpr){
				Collection<ReachingDU> rds = collectInvokeUses(stmt);
		        ruSet.addAll(rds);			    
			}
		}

	    return ruSet;	
	}
}
