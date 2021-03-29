package plover.guards;

import com.google.common.collect.Ordering;
import plover.soot.graph.GraphHelper;
import plover.soot.hammock.CFGEntry;
import plover.soot.hammock.CFGExit;
import plover.sootex.du.DefUseChain;
import plover.sootex.du.IReachingDUQuery;
import plover.sootex.location.AccessPath;
import plover.sootex.location.Location;
import plover.sootex.location.StackLocation;
import plover.sootex.sideeffect.ILocalityQuery;
import plover.sootex.sideeffect.SideEffectAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;

import soot.jimple.*;
import soot.tagkit.LineNumberTag;
import soot.tagkit.Tag;
import soot.toolkits.graph.*;

import java.util.*;
import java.util.stream.Collectors;


public class OverheadFinder {

    SootMethod method;
    UnitGraph cfg;
    /* reaching definition analysis */
    IReachingDUQuery rdAnalysis;
    /* reaching use analysis */
    IReachingDUQuery ruAnalysis;
    /* ud-chain and du-chain*/
    DefUseChain defUseChain;

    ILocalityQuery localityQuery;
    /* signatures of logging methods*/

    SideEffectAnalysis sideEffectAnalysis;
    List<String> loggingMethods;
    /* signatures of io methods*/
    List<String> ioMethods;
    /* guarded statements for IfStmt*/
    Map<Unit, Set<Unit>> guardedStmts;
    /* loops */
    Map<Unit, List<Stmt>> loops;
    /* back edge of loops */
    Set<Unit> backEdges;
    /* post-dorminator tree*/
    private DominatorTree<Unit> postDomTree;
    /* reverse topological order*/
    private List<Unit> reverseTopoOrder;
    /* if use control dependencies*/
    private boolean useControl;
    /**
     * starting <code>Unit</code> which are used for each iteration of the whole analysis
     */
    private Set<Unit> startingPoints = new HashSet<>();
    /**
     * working <code>Unit</code> which are detected as skippable in last run
     * and we need to detect if the DEF units for those untis are skippable
     */
    private Deque<Unit> workingList = new LinkedList<>();
    /* Call Stmt of Logging Methods*/
    Set<Unit> loggingCalls = new HashSet<>();
    /* skippable units according to logging statements*/
    Map<Unit, Set<String>> skippableUnits = new HashMap<>();
    /* unskippable units according to logging statements*/
    Set<Unit> unskippaleUnits = new HashSet<>();

    enum SkipStatus {
        SKIPPABLE,
        UNSKIPPABLE,
        UNKNOWN
    }

    public static final Logger LOGGER = LoggerFactory.getLogger(OverheadFinder.class);
    public static final Logger ID_LOGGER = LoggerFactory.getLogger("LoggingCallsID");

    public OverheadFinder(SootMethod method, UnitGraph cfg, IReachingDUQuery rdAnalysis, IReachingDUQuery ruAnalysis,
                          ILocalityQuery localityQuery, SideEffectAnalysis sideEffectAnalysis, List<String> loggingMethods,
                          List<String> ioMethods, boolean useControl) {
        this.method = method;
        this.cfg = cfg;
        this.rdAnalysis = rdAnalysis;
        this.ruAnalysis = ruAnalysis;
        this.localityQuery = localityQuery;
        this.sideEffectAnalysis = sideEffectAnalysis;
        this.loggingMethods = loggingMethods;
        this.ioMethods = ioMethods;
        this.useControl = useControl;
    }

    private void initialization() {
        this.guardedStmts = new HashMap<>();
        this.postDomTree = new DominatorTree<>(new MHGPostDominatorsFinder<Unit>(cfg));
        this.reverseTopoOrder = (new PseudoTopologicalOrderer<Unit>()).newList(cfg, true);

        this.loops = new HashMap<>();
        this.backEdges = new HashSet<>();
        Set<Loop> loops = LoopFinder.getLoops(cfg);
        for (Loop lp : loops) {
            List<Stmt> loopStmts = lp.getLoopStatements();
            this.backEdges.add(lp.backJump);

            // find first JumpOut IfStmt
            int index = 0;
            Stmt condStmt = null;
            boolean hasFound = false;
            for (; index < loopStmts.size(); index++) {
                Stmt cur = loopStmts.get(index);
                if (cur instanceof IfStmt && !loopStmts.contains(((IfStmt) cur).getTarget())) {
                    condStmt = cur;
                    hasFound = true;
                    break;
                }
            }
            List<Stmt> loopBlockBody;
            if (hasFound) {
                loopBlockBody = loopStmts.subList(index+1, loopStmts.size());
            } else {
                // when it is a infinite loop, like while(true), there is no JumpOut IfStmt in loopStmts
                loopBlockBody = loopStmts;
            }

            this.loops.put(condStmt, loopBlockBody);
        }

        this.defUseChain = new DefUseChain(rdAnalysis, ruAnalysis, reverseTopoOrder);
        defUseChain.initialization();

        unskippaleUnits.add(CFGEntry.v());
        unskippaleUnits.add(CFGExit.v());

        Set<Local> realLocals = localityQuery.getLocalityLocals(method);
        for (Unit unit : this.reverseTopoOrder) {
            String unitStr = unit.toString();

            Collection<AccessPath> defAccessPaths = defUseChain.getDefAccessPaths(unit);

            for (AccessPath defAp : defAccessPaths) {
                if (defAp != null && defAp.getAccessors() != null && defAp.length() > 0) {
                    Location defLocation = defAp.getRoot();
                    if (defLocation instanceof StackLocation) {
                        Value defValue = ((StackLocation) defLocation).getValue();
                        if (defValue instanceof Local && !realLocals.contains(defValue)) {
                            // workaround for <java.util.Set: int size()>()
                            if (!unit.toString().contains("<java.util.Set: int size()>()")) {
                                unskippaleUnits.add(unit);
                            }
                        }
                    }
                }
            }

            if (unit instanceof ReturnStmt) {
                unskippaleUnits.add(unit);
            } else if (unit instanceof IdentityStmt) {
                unskippaleUnits.add(unit);
            } else if (unit instanceof Stmt && ((Stmt) unit).containsInvokeExpr()) {
                SootMethod invokedMethod = ((Stmt) unit).getInvokeExpr().getMethod();
                String methodName = invokedMethod.getName();
                String className = invokedMethod.getDeclaringClass().getName();
                String qualifiedMethodName = String.join(".", className, methodName);
                for (String loggingMethod : loggingMethods) {
                    if (qualifiedMethodName.startsWith(loggingMethod)) {
                        // TODO assign uuid to logging calls
                        skippableUnits.put(unit, new HashSet<>(Arrays.asList(generateIDForLogging(this.method, unit))));
                        startingPoints.add(unit);
                        loggingCalls.add(unit);
                        break;
                    }
                }
                // if this invokes io method, we can not skip this unit
                if (ioMethods.contains(invokedMethod.getSignature())) {
                    unskippaleUnits.add(unit);
                }

                if (sideEffectAnalysis != null) {
                    if (sideEffectAnalysis.hasUnskippableSideEffect(invokedMethod)) {
                        unskippaleUnits.add(unit);
                    }
                }

            } else if (unit instanceof AssignStmt) {
                Value leftOp = ((AssignStmt) unit).getLeftOp();
                // TODO if assign to non-local variables, mark this unit as unskippable
//                Set locals = localityQuery.getLocalityLocals(method);
//                if (leftOp instanceof ArrayRef) {
//                    Value base = ((ArrayRef) leftOp).getBase();
//                    if (!locals.contains(base)) {
//                        unskippaleUnits.add(unit);
//                    }
//                }  else if (leftOp instanceof Local) {
//                    if (!locals.contains(leftOp)) {
//                        unskippaleUnits.add(unit);
//                    }
//                }
                if (leftOp instanceof InstanceFieldRef) {
                    unskippaleUnits.add(unit);
                }
                if (leftOp instanceof StaticFieldRef) {
                    unskippaleUnits.add(unit);
                }
            } else if (unit instanceof IfStmt) {
                // initialize guarded statements for IfStmt
                // get blocks guarded by this logging guard
                // 1. get all forward reachable nodes for both branch
                List<Unit> succs = cfg.getSuccsOf(unit);
                if (succs.size() != 2) throw new AssertionError();
                List<Unit> succ0 = Collections.singletonList(succs.get(0));
                List<Unit> succ1 = Collections.singletonList(succs.get(1));
                Set<Unit> reachable0 = GraphHelper.getReachables(cfg, succ0, backEdges);
                Set<Unit> reachable1 = GraphHelper.getReachables(cfg, succ1, backEdges);
                // 2. find the first common forward reachable node (we need to find all back edge first)
                List<Unit> commonReachable = new ArrayList<>(reachable0);
                commonReachable.retainAll(reachable1);
                commonReachable.remove(CFGExit.v());
                commonReachable.sort(Ordering.explicit(new ArrayList<>(cfg.getBody().getUnits())));
                commonReachable.add(CFGExit.v());
                if (commonReachable.isEmpty()) throw new AssertionError();
                if (commonReachable.size() != 1 || commonReachable.get(0) != CFGExit.v()) {
                    // 3.1. if the node is not EXIT node, get all forward reachable nodes for both branch
                    // until reaching this node and the keep the branch contains logging calls
                    Unit firstCommon = commonReachable.get(0);
                    List<Unit> endUnits = new ArrayList<>(backEdges);
                    endUnits.add(firstCommon);
                    Set<Unit> guardedUnits0 = GraphHelper.getReachables(cfg, succ0, endUnits);
                    Set<Unit> guardedUnits1 = GraphHelper.getReachables(cfg, succ1, endUnits);
                    Set<Unit> result = new HashSet<>();
                    result.addAll(guardedUnits0);
                    result.addAll(guardedUnits1);
                    guardedStmts.put(unit, result);
                } else {
                    // 3.2. if the node is EXIT node, which means at least one of the branch stop early
                    // (ends with return / throw) and doesn't merge with another branch.
                    LineNumberTag lineNumberTag = (LineNumberTag)unit.getTag("LineNumberTag");
                    LOGGER.warn("Both branch of IfStmt {} at line {} don't merge! Need manual examination!",
                            unit.toString(), lineNumberTag==null?"Unknown":lineNumberTag.getLineNumber());
                    Set<Unit> result = new HashSet<>();
                    result.addAll(reachable0);
                    result.addAll(reachable1);
                    guardedStmts.put(unit, result);
                }
            }
        }
    }

    public void doAnalysis() {
        initialization();

        while (!startingPoints.isEmpty()) {
            workingList.addAll(startingPoints);
            startingPoints.clear();
            // data dependence based identification
            while (!workingList.isEmpty()) {
                LOGGER.trace("Working list {}", workingList);
                Unit unit = workingList.removeFirst();
                List<Unit> defs = defUseChain.getDefUnitsOfUse(unit);
                LOGGER.trace("[DEF] {} for unit {}", defs, unit);
                defs.sort(Ordering.explicit(reverseTopoOrder));
                for (Unit def : defs) {
                    doDataBasedAnalysis(def, new ArrayList<>());
                }
            }

            // control dependence based identification
            if (useControl) {
                doControlBasedAnalysis();
            } else {
                break;
            }

            // startingPoints is not empty, another iteration will be conduct
            // we need to clear unskippaleUnits to guarantee all possible skippable units will be re-analyzed
            // FIXME maybe too conservative
//            if (!startingPoints.isEmpty()) {
//                unskippaleUnits.clear();
//            }
        }
        // TODO assign LoggingCallsID for Skippable Units
    }

    /**
     *
     * @param unit
     * @param underAnalysis
     */
    private SkipStatus doDataBasedAnalysis(Unit unit, List<Unit> underAnalysis) {
        // self-dependent, usually happens to loop variables, assume unknown now
        if (underAnalysis.contains(unit)) {
//            if (unit instanceof Stmt && ((Stmt) unit).containsInvokeExpr()) {
//                if (((Stmt) unit).getInvokeExpr().getMethod().getName().equals("<init>")) {
//                    return SkipStatus.SKIPPABLE;
//                }
//            }
            return SkipStatus.SKIPPABLE;
        }
        // already detected as skippable
        if (skippableUnits.containsKey(unit)) {
            return SkipStatus.SKIPPABLE;
        }
        // already detected as unskippable
        if (unskippaleUnits.contains(unit)) {
            return SkipStatus.UNSKIPPABLE;
        }
        // no matches, do analysis
        // if all uses of the def of this unit is skippable, then this unit is skippable
        SkipStatus skip = SkipStatus.SKIPPABLE;
        underAnalysis.add(unit);

        List<Unit> uses = defUseChain.getUseUnitsOfDef(unit);
        LOGGER.trace("[USE] {} for unit {}", uses, unit);
        if (uses != null && uses.size() > 0) {
            uses.sort(Ordering.explicit(reverseTopoOrder));
            for (Unit use : uses) {
                // TODO add a sanity check to skip cycle dependence
                SkipStatus skipStatusOfUse = doDataBasedAnalysis(use, underAnalysis);
                if (skipStatusOfUse == SkipStatus.UNSKIPPABLE) {
                    skip = SkipStatus.UNSKIPPABLE;
                    break;
                } else if (skipStatusOfUse == SkipStatus.UNKNOWN) {
                    skip = SkipStatus.UNKNOWN;
                }
            }
        }
        else {
            // no def, assume UNKNOWN now
            skip = SkipStatus.UNKNOWN;
            // FIXME maybe too conservative
            if (unit instanceof AssignStmt ) {
                // FIXME workaround for array access, in current implementation, array access has no reaching uses,
                //  However, doing so will introduce some false positive, we need to fix RUAnalysis to solve this problem
                if (((AssignStmt) unit).getLeftOp() instanceof ArrayRef) {
                    skip = SkipStatus.SKIPPABLE;
                }
            } else if (unit instanceof Stmt && ((Stmt) unit).containsInvokeExpr()) {
                // TODO workaround for cascading append,
                //  the receiver object of the next append is the return value of previous append,
                //  therefore the receiver object of the last append will not be used in other place explicitly
                //  and it will break the DefUse chain. We need to use alias analysis to handle this properly.

                if (((Stmt) unit).getInvokeExpr().getMethod().getSignature().contains("append")) {
                    skip = SkipStatus.SKIPPABLE;
                } else {
                    skip = SkipStatus.UNSKIPPABLE;
                }

            }
        }

        underAnalysis.remove(unit);
        // if not SkipStatus.UNSKIPPABLE, the unit is skippable
        if (skip == SkipStatus.SKIPPABLE) {
            LOGGER.trace("Skippable unit {}", unit);
            skippableUnits.put(unit, generateIDForNonLoging(unit));
            workingList.addFirst(unit);
        } else if (skip == SkipStatus.UNSKIPPABLE) {
            LOGGER.trace("Unskippable unit {}", unit);
            unskippaleUnits.add(unit);
        } else {
            // SkipStatus.UNKNOWN
            // do nothing
        }
        return skip;
    }


    private void doControlBasedAnalysis() {
        for (Unit unit : reverseTopoOrder) {
            if (unit instanceof IfStmt && !skippableUnits.containsKey(unit) && !unskippaleUnits.contains(unit)) {
                // loop control
                if (loops.containsKey(unit)) {
                    List<Stmt> loopBlockBody = loops.get(unit);
                    List<Value> condValues = unit.getUseBoxes().stream()
                            .map(ValueBox::getValue)
                            .collect(Collectors.toList());

                    List<Stmt> unmatchedStmts = new ArrayList<>();
                    for (Stmt stmt : loopBlockBody) {
                        if (!skippableUnits.containsKey(stmt)) {
                            unmatchedStmts.add(stmt);
                        }
                    }

                    boolean allSkippable = true;
                    for (Stmt unmatchedUnit : unmatchedStmts) {
                        if (unmatchedUnit instanceof GotoStmt) {
                            // unconditional jump, do nothing
                        } else if (unmatchedUnit instanceof AssignStmt) {

                            // if it is loop variables modification
                            List<Value> leftValues = unmatchedUnit.getDefBoxes().stream()
                                    .map(valueBox -> valueBox.getValue())
                                    .collect(Collectors.toList());
                            boolean hasCommon = false;
                            // if the left values share some elements with condition values,
                            // we aggressively take it as loop variables modification
                            for (Value value : leftValues) {
                                if (condValues.contains(value)) {
                                    hasCommon = true;
                                    break;
                                }
                            }

                            boolean isIteratorNext = false;
                            if (unmatchedUnit.containsInvokeExpr()) {
                                // workaround for java.util.Iterator: java.lang.Object next()
                                SootMethod callee = unmatchedUnit.getInvokeExpr().getMethod();
                                if (callee.getSignature().contains("next()")) {
                                    isIteratorNext = true;
                                }
                            }

                            // if not loop variables modification, can not skip
                            if (!hasCommon && !isIteratorNext) {
                                allSkippable = false;
                                break;
                            }
                        } else {
                            allSkippable = false;
                            break;
                        }

                    }
                    if (allSkippable) {
                        Set<String> ids = new HashSet<>();
                        for (Stmt stmt : loopBlockBody) {
                            ids.addAll(generateIDForNonLoging(stmt));
                        }
                        skippableUnits.put(unit, ids);
                        startingPoints.add(unit);
                        for (Unit unmatchedUnit : unmatchedStmts) {
                            skippableUnits.put(unmatchedUnit, ids);
                            startingPoints.add(unmatchedUnit);
                        }
                    }
                }
                // if control
                else {
                    Set<Unit> stmts = guardedStmts.get(unit);
                    if (stmts == null || stmts.size() == 0) {
                        LOGGER.warn("Guarded statements for {} is null or zero", unit);
                        continue;
                    }
                    List<Unit> unmatchedStmts = new ArrayList<>();
                    for (Unit stmt : stmts) {
                        if (!skippableUnits.containsKey(stmt)) {
                            unmatchedStmts.add(stmt);
                        }
                    }
                    boolean allSkippable = true;
                    for (Unit stmt : unmatchedStmts) {
                        // TODO maybe too aggressive
                        //  need to check whether the target unit of GotoStmt is exactly the unit after the if-else block
                        if (stmt instanceof GotoStmt) {
                            continue;
                        }
                        if (stmt instanceof ReturnVoidStmt) {
                            continue;
                        }
                        allSkippable = false;
                        break;
                    }
                    if (allSkippable) {
                        // all statements are skippable, the if statement is also skippable
                        Set<String> ids = new HashSet<>();
                        for (Unit stmt : stmts) {
                            ids.addAll(generateIDForNonLoging(stmt));
                        }
                        skippableUnits.put(unit, ids);
                        startingPoints.add(unit);
                        for (Unit unmatchedUnit : unmatchedStmts) {
                            skippableUnits.put(unmatchedUnit, ids);
                            startingPoints.add(unmatchedUnit);
                        }
                    }

                }
            }
        }
    }

    private Set<String> generateIDForNonLoging(Unit unit) {
        Set<String> ids = new HashSet<>();
        Deque<Unit> workingList = new LinkedList<>();
        workingList.addFirst(unit);
        List<Unit> visited = new ArrayList<>();
        while (!workingList.isEmpty()) {
            Unit cur = workingList.removeFirst();
            if (!cur.equals(unit)) {
                if (skippableUnits.get(cur) == null) {
                    ids.addAll(Arrays.asList("UNKNOWN_ID"));
                } else {
                    ids.addAll(skippableUnits.get(cur));
                }

            }
            if (!visited.contains(cur)) {
                visited.add(cur);
                List<Unit> useOfDef = defUseChain.getUseUnitsOfDef(cur);
                if (useOfDef != null) {
                    workingList.addAll(useOfDef);
                }
            }
        }
        return ids;
    }

    static String generateIDForLogging(SootMethod method, Unit unit) {
        StringBuilder loggingCall = new StringBuilder();
        loggingCall.append(method.getSignature());
        loggingCall.append(unit.toString());
        Tag tag = unit.getTag("LineNumberTag");
        String lineNum = tag==null?"null":tag.toString();
        loggingCall.append(lineNum);
        String id = UUID.nameUUIDFromBytes(loggingCall.toString().getBytes()).toString().substring(0, 13);
        ID_LOGGER.info("Assign ID: {} to {} at line {} of method {}", id, unit.toString(), lineNum, method.getSignature());
        return id;
    }

}
