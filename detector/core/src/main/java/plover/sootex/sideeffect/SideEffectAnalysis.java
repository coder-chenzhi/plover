package plover.sootex.sideeffect;

import java.util.*;

import com.google.common.collect.Ordering;
import plover.soot.Cache;
import plover.soot.callgraph.Callees;
import plover.sootex.location.*;
import plover.sootex.ptsto.IPtsToQuery;
import plover.soot.SootUtils;
import plover.soot.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.*;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.PseudoTopologicalOrderer;


/**
 * Context-insensitive side effect information collector.
 * When using context-insensitive pointer analysis, the side-effect sets of a method can be huge.
 *
 */
@SuppressWarnings({"rawtypes","unchecked"})
public class SideEffectAnalysis implements ISideEffectAnalysis{
    public static final Logger LOGGER = LoggerFactory.getLogger(SideEffectAnalysis.class);
    public static int MAX_ITERATION = 100;

    private Collection entries;
    private IPtsToQuery ptsto;
    private CallGraph cg;
    private ILocalityQuery localityQuery;
    private MustAliasIdentityLocalsQuery mustAliasQuery;
    private List<String> ioMethods;
    
    private Set<AccessPath>[] method2ModHeaps;
    private Set<AccessPath>[] method2UseHeaps;
    private Boolean[] method2Unskippable;

    public static List<String> knownSkippableMethods = new ArrayList<>(
            Arrays.asList(
                    "boolean isDebugEnabled",
                    "boolean isTraceEnabled",
                    "java.util.Set keySet()",
                    "java.util.Collection values()",
                    "java.util.Iterator iterator()",
                    "java.lang.Object next()",
                    "boolean hasNext()",
                    "java.lang.Class getClass()",
                    "java.lang.String getName()",
                    "java.lang.String getPath()",
                    "java.io.File: long length()",
                    "java.lang.Long valueOf(long)",
                    "java.lang.Double valueOf(double)",
                    "java.lang.Integer valueOf(int)"
            )
    );


    
    public SideEffectAnalysis(IPtsToQuery ptsto, Collection entries, List<String> ioMethods){
    	this.entries = entries;
    	this.ptsto = ptsto;
    	this.ioMethods = ioMethods;
    }

    public SideEffectAnalysis(IPtsToQuery ptsto, MustAliasIdentityLocalsQuery mustAliasQuery,
                              ILocalityQuery localityQuery, Collection entries, List<String> ioMethods){
        this.ptsto = ptsto;
        this.mustAliasQuery = mustAliasQuery;
        this.localityQuery = localityQuery;
        this.entries = entries;
        this.ioMethods = ioMethods;
    }


    @Override
    public Collection<AccessPath> getModHeapLocs(SootMethod method){
        return method2ModHeaps[method.getNumber()];
    }   

    @Override
    public Collection<AccessPath> getUseHeapLocs(SootMethod method){
        return method2UseHeaps[method.getNumber()];
    }

    @Override
    public boolean hasUnskippableSideEffect(SootMethod method) {
        boolean unskip = false;

        try {
            unskip = method2Unskippable[method.getNumber()];
        } catch (NullPointerException e) {

        }

        return unskip;
    }

    void clearMethod(int id){    	 
        method2ModHeaps[id] = null;
        method2UseHeaps[id] = null;
        method2Unskippable[id] = null;
        mustAliasQuery = null;
    }
    
    private Set<InstanceObject> collectObjects(Collection<Location> locations){
    	Set<InstanceObject> objects = new HashSet<InstanceObject>();
    	for(Location loc: locations){
    		if(loc instanceof HeapLocation){
    			HeapLocation hLoc = (HeapLocation)loc;
    			objects.add(hLoc.getWrapperObject());
    		}
    	}
    	return objects;
    }
    
//    public Collection<InstanceObject> getModObjects(SootMethod m){
//    	Collection<Location> locations = getModHeapLocs(m);
//    	return  collectObjects(locations);
//    }
//
//    public Collection<InstanceObject> getUseObjects(SootMethod m){
//    	Collection<Location> locations = getUseHeapLocs(m);
//    	return  collectObjects(locations);
//    }
    
	public void build(){    	
        Date startBuild = new Date();  
        
        int methodNum = SootUtils.getMethodCount();
        cg =  Scene.v().getCallGraph();
        method2ModHeaps = new Set[methodNum];
        method2UseHeaps = new Set[methodNum];
        method2Unskippable = new Boolean[methodNum];

        // 1. get the collapse call graph, each strong connected component into a single graph node
        LOGGER.info("[SideEffect] collapse call graph...");
   		DirectedGraph<List<SootMethod>> componentCallGraph = SootUtils.getSCCGraphFast(cg, entries);
        Cache.v().setComponentCallGraph(componentCallGraph);

   		// 2. topological sort
        LOGGER.info("[SideEffect] topological sort methods...");
   		PseudoTopologicalOrderer pto = new PseudoTopologicalOrderer();
        List componentsOrder = pto.newList(componentCallGraph,true);
        List methodOrder = Cache.v().getReverseTopologicalOrder();


        if (LOGGER.isDebugEnabled()) {
            for (Iterator it=componentsOrder.iterator();it.hasNext();){
                Collection node = (Collection) it.next();
                if (node.size() > 100) {
                    LOGGER.debug("large scc size: {}", node.size());
//                    for (Object method : node) {
//                        SootMethod sootMethod = (SootMethod) method;
//                        if (sootMethod.isConcrete()) {
//                            List edgeInto = ImmutableList.copyOf(cg.edgesInto(sootMethod));
//                            List edgeOut = ImmutableList.copyOf(cg.edgesOutOf(sootMethod));
//                            LOGGER.debug("{}\t{}\t{}", sootMethod, edgeInto.size(), edgeOut.size());
//                        }
//                    }
                }
            }
        }

        // 3. Must alias analysis
        if (mustAliasQuery == null) {
            LOGGER.info("[AliasAnalysis] must alias to identity locals analysis...");
            mustAliasQuery = new MustAliasIdentityLocalsQuery();
            mustAliasQuery.build();
        }

        // 4. Locality analysis
        if (localityQuery == null) {
            LOGGER.info("[SideEffect] locality analysis...");
            FastEscapeAnalysis escape = new FastEscapeAnalysis(cg, mustAliasQuery);
            escape.build();
            localityQuery = escape;
        }

        // 5. bottom-up phase to find unskippable side-effect
        LOGGER.info("[SideEffect] bottom-up phase to find unskippable side-effect...");
        for (Iterator it=componentsOrder.iterator();it.hasNext();){
        	Collection node = (Collection) it.next();
        	findUnskippableSideEffectsForComponent(node);
        }
        	
        // 6. bottom-up phase to find read/write on this local and parameter locals
        LOGGER.info("[SideEffect] intra-procedure analysis to find read/write on identity locals...");
        // 6.1 intra-procedural analysis for each method (no need to care about component)
        LOGGER.info("[SideEffect] bottom-up phase to find read/write on identity locals...");
        for (Iterator it=componentsOrder.iterator();it.hasNext();){
            Collection node = (Collection) it.next();
            for (Object method : node) {
                SootMethod sootMethod = (SootMethod) method;
                findIntraThisParaSideEffectsForMethod(sootMethod);
            }
        }
        // 6.2 inter-procedural analysis for each component
        LOGGER.info("[SideEffect] inter-procedure analysis to find read/write on identity locals...");
        for (Iterator it=componentsOrder.iterator();it.hasNext();){
        	List node = new ArrayList((Collection) it.next());
        	node.sort(Ordering.explicit(methodOrder));
        	findInterThisParaSideEffectsForComponent(node);
        }
        
		// free memories
		entries = null;
		ptsto = null;

		String sbAppendMethodSig = "<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>";
		SootMethod sbAppendMethod = Scene.v().getMethod(sbAppendMethodSig);
		String absSbAppendMethodSig = "<java.lang.AbstractStringBuilder: java.lang.AbstractStringBuilder append(java.lang.String)>";
		SootMethod absSbAppendMethod = Scene.v().getMethod(absSbAppendMethodSig);
		LOGGER.debug("side-effect for {} is {}", sbAppendMethodSig, method2ModHeaps[sbAppendMethod.getNumber()]);
		LOGGER.debug("side-effect for {} is {}", absSbAppendMethodSig, method2ModHeaps[absSbAppendMethod.getNumber()]);

        Date endBuild = new Date();        
        LOGGER.info("[SideEffect] complete in  {}", Utils.getTimeConsumed(startBuild,endBuild));
    }

    /**
     * find unskippable side-effects for component
     * There are only two status for unskippable side-effects of methods in one component
     * all methods have unskippable side-effects or none of methods have unskippable side-effects
     * @param methods
     */
    private void findUnskippableSideEffectsForComponent(Collection methods){
        boolean hasUnskippableEffects = false;
        for (Object method : methods) {
            if (findUnskippableSideEffectsForMethod((SootMethod) method)) {
                hasUnskippableEffects = true;
                break;
            }
        }
        if (hasUnskippableEffects) {
            for (Object method : methods) {
                method2Unskippable[((SootMethod) method).getNumber()] = true;
            }
        } else {
            for (Object method : methods) {
                method2Unskippable[((SootMethod) method).getNumber()] = false;
            }
        }
    }

    private boolean findUnskippableSideEffectsForMethod(SootMethod method) {
        // FIXME need to use more reliable approach to findUnskippableSideEffectsForMethod
        return false;

        /*
        if (!method.isConcrete())
            return false;
        MustAliasIdentityLocalsAnalysis mustAliasAnalysis = mustAliasQuery.query(method);
        Set<Local> localityLocalVars = localityQuery.getLocalityLocals(method);
        Set<Local> realEscapeLocalVars = localityQuery.getRealEscapedLocals(method);

        for (Unit stmt : method.getActiveBody().getUnits()) {
            List<ValueBox> defBoxes = stmt.getDefBoxes();
            for (ValueBox box : defBoxes) {
                Value v = box.getValue();
                // if write static field
                if (v instanceof StaticFieldRef) {
                    return true;
                }


                // if write instance field or array
                if (v instanceof InstanceFieldRef || v instanceof ArrayRef) {
                    Local base;
                    if (v instanceof InstanceFieldRef) {
                        base = (Local) ((InstanceFieldRef) v).getBase();
                    } else {
                        base = (Local) ((ArrayRef) v).getBase();
                    }

                    // write non-local instance field store
                    if (!localityLocalVars.contains(base)) {
                        // write non-local non-this/non-parameters instance field
                        if (!mustAliasAnalysis.isMustAliasToIdentityLocals(base, (Stmt) stmt)) {
                            return true;
                        }
                        if (realEscapeLocalVars == null) {
                            System.out.println("debug");
                        }
                        // write this local or parameter locals,
                        // but the written local is escaped from other sink
                        else if (realEscapeLocalVars.contains(base)) {
                            return true;
                        }
                    }
                }

            }

            if (stmt instanceof Stmt && ((Stmt) stmt).containsInvokeExpr()) {
                Callees callees = new Callees(cg, stmt);
                Set<SootMethod> tgtMethod = callees.explicits();
                // workaround to handle the case where spark miss many edges (such as println methods)
                // FIXME need to figure out why spark will miss many edges
                tgtMethod.add(((Stmt) stmt).getInvokeExpr().getMethod());

                for (SootMethod callee : tgtMethod) {
                    // if perform i/o method
                    if (ioMethods.contains(callee.getSignature())) {
                        return true;
                    }
                    // if callee has unskippable side-effect
                    if (method2Unskippable[callee.getNumber()] != null && method2Unskippable[callee.getNumber()]) {
                        return true;
                    }
                }

            }
        }
        */
    }

    private void findIntraThisParaSideEffectsForMethod(SootMethod method) {
        if (!method.isConcrete())
            return;

        LOGGER.trace("[SideEffect] intra-procedure analysis for {}", method.getSignature());
        MustAliasIdentityLocalsAnalysis mustAliasAnalysis = mustAliasQuery.query(method);
        Set<Local> localityLocalVars = localityQuery.getLocalityLocals(method);
        Set<Local> realEscapeLocalVars = localityQuery.getRealEscapedLocals(method);
        Set<AccessPath> mod = new HashSet<>();
        Set<AccessPath> use = new HashSet<>();

        for (Unit stmt : method.getActiveBody().getUnits()) {
            List<ValueBox> defBoxes = stmt.getDefBoxes();
            List<ValueBox> useBoxes = stmt.getUseBoxes();

            for (ValueBox box : defBoxes) {
                Value defValue = box.getValue();

                // if write instance field or array
                if (defValue instanceof InstanceFieldRef || defValue instanceof ArrayRef) {
                    Local base;
                    if (defValue instanceof InstanceFieldRef) {
                        base = (Local) ((InstanceFieldRef) defValue).getBase();
                    } else {
                        base = (Local) ((ArrayRef) defValue).getBase();
                    }

                    // write non-local instance field store and is not real escape
                    if (!localityLocalVars.contains(base) && !realEscapeLocalVars.contains(base)) {
                        // write this local or parameter locals
                        Set<AccessPath> aliasLocals = mustAliasAnalysis.getMustAliasToIdentityLocals(base, (Stmt) stmt);
                        if (!aliasLocals.isEmpty()) {
                            for (AccessPath ap : aliasLocals) {
                                AccessPath newAp;
                                if (defValue instanceof InstanceFieldRef) {
                                    newAp = ap.appendFieldRef(((InstanceFieldRef) defValue).getField());
                                } else {
                                    // ap=ap.appendArrayRef(((ArrayRef)value).getIndex());
                                    newAp = ap.appendArrayRef();
                                }
                                mod.add(newAp);
                            }
                        }
                    }
                }
            }

            for (ValueBox box : useBoxes) {
                Value useValue = box.getValue();

                // TODO add branch to handle if (useValue instanceof Local)
                // if read instance field or array
                if (useValue instanceof InstanceFieldRef || useValue instanceof ArrayRef) {
                    Local base;
                    if (useValue instanceof InstanceFieldRef) {
                        base = (Local) ((InstanceFieldRef) useValue).getBase();
                    } else {
                        base = (Local) ((ArrayRef) useValue).getBase();
                    }

                    // read non-local instance field
                    if (!localityLocalVars.contains(base)  && !realEscapeLocalVars.contains(base)) {
                        // read this local or parameter locals
                        Set<AccessPath> aliasLocals = mustAliasAnalysis.getMustAliasToIdentityLocals(base, (Stmt) stmt);
                        if (!aliasLocals.isEmpty()) {
                            for (AccessPath ap : aliasLocals) {
                                AccessPath newAp;
                                if (useValue instanceof InstanceFieldRef) {
                                    newAp = ap.appendFieldRef(((InstanceFieldRef) useValue).getField());
                                } else {
                                    // ap=ap.appendArrayRef(((ArrayRef)value).getIndex());
                                    newAp = ap.appendArrayRef();
                                }
                                use.add(newAp);
                            }
                        }
                    }
                }
            }
        }
        boolean skip = false;
        for (String skipMethod : knownSkippableMethods) {
            if (method.toString().contains(skipMethod)) {
                skip = true;
                break;
            }
        }

        if (skip) {
            method2ModHeaps[method.getNumber()] = new HashSet<>();
            method2UseHeaps[method.getNumber()] = use;
        } else {
            method2ModHeaps[method.getNumber()] = mod;
            method2UseHeaps[method.getNumber()] = use;
        }

    }

    /**
     * method in methods should be reverse topologically ordered.
     * @param methods
     */
    private void findInterThisParaSideEffectsForComponent(List methods){
    	// inter-procedural analysis for the callee which IS NOT in this component
        LOGGER.trace("[SideEffect] inter-procedure analysis for component {} size {}", methods.get(0), methods.size());
 		for (Object method : methods) {
            SootMethod sootMethod = (SootMethod) method;
            if (!sootMethod.isConcrete()) {
                continue;
            }
            Set<AccessPath> mod = new HashSet();
            Set<AccessPath> use = new HashSet();
            MustAliasIdentityLocalsAnalysis mustAliasAnalysis = mustAliasQuery.query(sootMethod);
            Set<Local> localityLocalVars = localityQuery.getLocalityLocals(sootMethod);
            Set<Local> realEscapeLocalVars = localityQuery.getRealEscapedLocals(sootMethod);
            Body body = sootMethod.retrieveActiveBody();
            for (Unit stmt : body.getUnits()) {
                if (stmt instanceof Stmt && ((Stmt) stmt).containsInvokeExpr()) {
                    Callees callees = new Callees(cg, stmt);
                    for (SootMethod callee : callees.explicits()) {
                        // the callee is not in this component
                        if (callee.isConcrete() && !methods.contains(callee)) {
                            Set<AccessPath> modHeap = method2ModHeaps[callee.getNumber()];
                            Set<AccessPath> transitModHeap = getMappingAccessPath((Stmt) stmt, callee, modHeap);
                            LOGGER.trace("modHeap {} transitModHeap {}", modHeap, transitModHeap);

                            for (AccessPath suffix : transitModHeap) {
                                if (!(suffix.getRoot() instanceof StackLocation)) {
                                    continue;
                                }
                                Value base = ((StackLocation) suffix.getRoot()).getValue();
                                if (!localityLocalVars.contains(base) && !realEscapeLocalVars.contains(base)) {
                                    Set<AccessPath> aliasLocals = mustAliasAnalysis.getMustAliasToIdentityLocals((Local) base, (Stmt) stmt);
                                    for (AccessPath prefix : aliasLocals) {
                                        mod.add(prefix.appendAccessors(Arrays.asList(suffix.getAccessors())));
                                    }
                                }
                            }
                            Set<AccessPath> useHeap = method2UseHeaps[callee.getNumber()];
                            Set<AccessPath> transitUseHeap = getMappingAccessPath((Stmt) stmt, callee, useHeap);
                            for (AccessPath suffix : transitUseHeap) {
                                if (!(suffix.getRoot() instanceof StackLocation)) {
                                    continue;
                                }
                                Value base = ((StackLocation) suffix.getRoot()).getValue();
                                if (!localityLocalVars.contains(base) && !realEscapeLocalVars.contains(base)) {
                                    Set<AccessPath> aliasLocals = mustAliasAnalysis.getMustAliasToIdentityLocals((Local) base, (Stmt) stmt);
                                    for (AccessPath prefix : aliasLocals) {
                                        use.add(prefix.appendAccessors(Arrays.asList(suffix.getAccessors())));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            boolean skip = false;
            for (String skipMethod : knownSkippableMethods) {
                if (sootMethod.toString().contains(skipMethod)) {
                    skip = true;
                    break;
                }
            }
            if (skip) {
                method2UseHeaps[sootMethod.getNumber()].addAll(use);
            } else {
                method2ModHeaps[sootMethod.getNumber()].addAll(mod);
                method2UseHeaps[sootMethod.getNumber()].addAll(use);
            }
        }

        // inter-procedural analysis for the callee which IS in this component
        // and we need to run multiple times until reach fixed point

        boolean changed = true;

        int startIndex = 0;
        int size = methods.size();
        int iteration = 0;
 		while (changed) {
 		    if (iteration > MAX_ITERATION) {
 		        LOGGER.warn("[SideEffect] inter-procedure analysis for component {} size: {} iteration: {} too many iterations ",
                        methods.get(0), methods.size(), iteration);
 		        break;
            }
 		    iteration++;
 		    changed = false;
            LOGGER.trace("[SideEffect] inter-procedure analysis for component {} iteration: {}", methods.get(0), iteration);
            for (int i = 0; i < size; i++) {
                int index = (startIndex + i) % size;
                SootMethod sootMethod = (SootMethod) methods.get(index);
                if (!sootMethod.isConcrete()) {
                    continue;
                }
                Set<AccessPath> mod = new HashSet();
                Set<AccessPath> use = new HashSet();
                MustAliasIdentityLocalsAnalysis mustAliasAnalysis = mustAliasQuery.query(sootMethod);;
                Set<Local> localityLocalVars = localityQuery.getLocalityLocals(sootMethod);
                Set<Local> realEscapeLocalVars = localityQuery.getRealEscapedLocals(sootMethod);
                LOGGER.trace("localityLocalVars {} realEscapeLocalVars {}", localityLocalVars, realEscapeLocalVars);

                Body body = sootMethod.retrieveActiveBody();
                for (Unit stmt : body.getUnits()) {
                    if (stmt instanceof Stmt && ((Stmt) stmt).containsInvokeExpr()) {
                        Callees callees = new Callees(cg, stmt);
                        for (SootMethod callee : callees.explicits()) {
                            // the callee is in this component
                            if (callee.isConcrete() && methods.contains(callee)) {
                                Set<AccessPath> modHeap = method2ModHeaps[callee.getNumber()];
                                Set<AccessPath> transitModHeap = getMappingAccessPath((Stmt) stmt, callee, modHeap);
                                LOGGER.trace("modHeap {} transitModHeap {}", modHeap, transitModHeap);

                                for (AccessPath suffix : transitModHeap) {

                                    LOGGER.trace("!(suffix.getRoot() instanceof StackLocation) {}", !(suffix.getRoot() instanceof StackLocation));

                                    if (!(suffix.getRoot() instanceof StackLocation)) {
                                        continue;
                                    }
                                    Value base = ((StackLocation) suffix.getRoot()).getValue();

                                    LOGGER.trace("!localityLocalVars.contains(base) {} !realEscapeLocalVars.contains(base) {}",
                                                !localityLocalVars.contains(base), !realEscapeLocalVars.contains(base));

                                    if (!localityLocalVars.contains(base) && !realEscapeLocalVars.contains(base)) {
                                        Set<AccessPath> aliasLocals = mustAliasAnalysis.getMustAliasToIdentityLocals((Local) base, (Stmt) stmt);

                                        LOGGER.trace("aliasLocals {}", aliasLocals);

                                        for (AccessPath prefix : aliasLocals) {
                                            mod.add(prefix.appendAccessors(Arrays.asList(suffix.getAccessors())));
                                        }
                                    }
                                }
                                Set<AccessPath> useHeap = method2UseHeaps[callee.getNumber()];
                                Set<AccessPath> transitUseHeap = getMappingAccessPath((Stmt) stmt, callee, useHeap);
                                for (AccessPath suffix : transitUseHeap) {
                                    if (!(suffix.getRoot() instanceof StackLocation)) {
                                        continue;
                                    }
                                    Value base = ((StackLocation) suffix.getRoot()).getValue();
                                    if (!localityLocalVars.contains(base) && !realEscapeLocalVars.contains(base)) {
                                        Set<AccessPath> aliasLocals = mustAliasAnalysis.getMustAliasToIdentityLocals((Local) base, (Stmt) stmt);
                                        for (AccessPath prefix : aliasLocals) {
                                            use.add(prefix.appendAccessors(Arrays.asList(suffix.getAccessors())));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Set<AccessPath> preModHeaps = method2ModHeaps[sootMethod.getNumber()];

                LOGGER.trace("preModHeaps {} mod {}", preModHeaps, mod);

                Set<AccessPath> preUseHeaps = method2UseHeaps[sootMethod.getNumber()];
                if (!preModHeaps.containsAll(mod)) {
                    // There are some changes in this iteration,
                    // we need to do another iteration until reaching fixed point
                    // Should we change the start index for next iteration? I guess not.
                    changed = true;
                    preModHeaps.addAll(mod);
                }
                if (!preUseHeaps.containsAll(use)) {
                    changed = true;
                    preUseHeaps.addAll(use);
                }
            }
        }
    }

    public Set<AccessPath> getMappingAccessPath(Stmt stmt, SootMethod callee, Collection<AccessPath> calleeAP) {
        Set<AccessPath> mappingAp = new HashSet<>();
        callee.retrieveActiveBody();
        Body calleeBody = callee.getActiveBody();
        for (AccessPath ap : calleeAP) {
            Location root = ap.getRoot();
            if (root instanceof StackLocation) {
                Value rootValue = ((StackLocation) root).getValue();
                if (!callee.isStatic() && rootValue.equals(calleeBody.getThisLocal())) {
                    InvokeExpr invoke = stmt.getInvokeExpr();
                    if (invoke instanceof InstanceInvokeExpr)
                    mappingAp.add(ap.getRootModified(Location.valueToLocation(((InstanceInvokeExpr) invoke).getBase())));
                }
                for (int i = 0; i < callee.getParameterCount(); i++) {
                    if (rootValue.equals(calleeBody.getParameterLocal(i))) {
                        mappingAp.add(ap.getRootModified(Location.valueToLocation(stmt.getInvokeExpr().getArg(i))));
                    }
                }
            }
        }
        return mappingAp;
    }
}
