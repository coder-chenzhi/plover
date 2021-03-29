package plover.soot;

import com.google.common.collect.Ordering;
import plover.soot.graph.GraphHelper;
import plover.soot.hammock.CFGExit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.*;
import soot.tagkit.LineNumberTag;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.*;

/**
 * A forward data-flow analysis to detect logging guards and guarded statements
 * TODO: In our current implementation, multiple conditional blocks for one logging guards will be taken as different logging guards
 *  To solve this problem, we need to track the propagation of logging guards to keep the equivalence relations of logging guards.
 */
public class GuardFinder extends ForwardFlowAnalysis<Unit, FlowSet> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuardFinder.class);

    private List<String> guardMethodSigs;
    private List<String> loggingMethodSigs;
    private FlowSet emptySet;
    private HashMap<Unit, Set<Unit>> guardedStmts;
    private UnitGraph graph;
    private Set<Unit> backEdges;
    private DominatorTree<Unit> postDomTree;


    public HashMap<Unit, Set<Unit>> getGuardedStmts() {
        return guardedStmts;
    }

    public GuardFinder(UnitGraph graph, List<String> loggingMethodSigs, List<String> guardMethodSigs) {
        super(graph);
        this.graph = graph;
        this.guardMethodSigs = guardMethodSigs;
        this.loggingMethodSigs = loggingMethodSigs;
        this.emptySet = new ArraySparseSet();
        this.guardedStmts = new HashMap<>();
        this.postDomTree = new DominatorTree<>(new MHGPostDominatorsFinder<Unit>(graph));
        this.backEdges = new HashSet<>();
        findBackEdge();
        doAnalysis();
    }

    private void findBackEdge() {
        DominatorsFinder<Unit> a = new MHGDominatorsFinder<>(graph);
        Map<Stmt, List<Stmt>> loops = new HashMap<Stmt, List<Stmt>>();

        for (Unit u : graph.getBody().getUnits()) {
            List<Unit> succs = graph.getSuccsOf(u);
            if (succs == null) {
                continue;
            }
            List<Unit> dominaters = a.getDominators(u);
            List<Stmt> headers = new ArrayList<Stmt>();

            for (Unit succ : succs) {
                if (dominaters.contains(succ)) {
                    // succ succeeds and dominates header, we have a loop
                    backEdges.add(u);
                    break;
                }
            }
        }
    }

    @Override
    protected void flowThrough(FlowSet in, Unit unit, FlowSet out) {
        FlowSet gen = new ArraySparseSet();
        FlowSet kill = new ArraySparseSet();
        // Gen and Kill
        // All the useful logging guards should be DefinitionStmt. The return value of InvokeStmt is no use.
        if (unit instanceof DefinitionStmt) {
            boolean isLoggingGuard = false;
            // invoke logging guards methods
            if(((DefinitionStmt) unit).getRightOp() instanceof InvokeExpr) {
                String methodRef = ((InvokeExpr) ((DefinitionStmt) unit).getRightOp()).getMethodRef().getSignature();
                for (String sig : guardMethodSigs) {
                    if (sig.equals(methodRef)) {
                        isLoggingGuard = true;
                        break;
                    }
                }
            }
            // use logging guards values
            // if the logging guards values are used, the def is taken as logging guard value
            // FIXME maybe too aggressive
            if (!isLoggingGuard) {
                List<ValueBox> useBoxes = unit.getUseBoxes();
                for (ValueBox box : useBoxes) {
                    Value use = box.getValue();
                    if (in.contains(use)) {
                        isLoggingGuard = true;
                        break;
                    }
                }
            }
            if (isLoggingGuard) {
                gen.add(unit.getDefBoxes().get(0).getValue());
            } else {
                kill.add(unit.getDefBoxes().get(0).getValue());
            }
        }
        if (unit instanceof IfStmt){
            Boolean isLoggingGuard = false;
            List<ValueBox> useBoxes = unit.getUseBoxes();
            for (ValueBox box : useBoxes) {
                Value use = box.getValue();
                if (in.contains(use)) {
                    isLoggingGuard = true;
                    break;
                }
            }
            if (isLoggingGuard) {
                // initialize guarded statements for logging guard
                // get blocks guarded by this logging guard
                // 1. get all forward reachable nodes for both branch
                List<Unit> succs = graph.getSuccsOf(unit);
                if (succs.size() != 2) throw new AssertionError();
                List<Unit> succ0 = Collections.singletonList(succs.get(0));
                List<Unit> succ1 = Collections.singletonList(succs.get(1));
                Set<Unit> reachable0 = GraphHelper.getReachables(graph, succ0, backEdges);
                Set<Unit> reachable1 = GraphHelper.getReachables(graph, succ1, backEdges);
                // 2. find the first common forward reachable node (we need to find all back edge first)
                List<Unit> commonReachable = new ArrayList<>(reachable0);
                commonReachable.retainAll(reachable1);
                commonReachable.remove(CFGExit.v());
                commonReachable.sort(Ordering.explicit(new ArrayList<>(graph.getBody().getUnits())));
                commonReachable.add(CFGExit.v());
                if (commonReachable.isEmpty()) throw new AssertionError();
                if (commonReachable.size() != 1 || commonReachable.get(0) != CFGExit.v()) {
                    // 3.1. if the node is not EXIT node, get all forward reachable nodes for both branch
                    // until reaching this node and the keep the branch contains logging calls
                    Unit firstCommon = commonReachable.get(0);
                    List<Unit> endUnits = new ArrayList<>(backEdges);
                    endUnits.add(firstCommon);
                    Set<Unit> guardedUnits0 = GraphHelper.getReachables(graph, succ0, endUnits);
                    Set<Unit> guardedUnits1 = GraphHelper.getReachables(graph, succ1, endUnits);
                    boolean hasLoggingCalls0 = hasLoggingCalls(guardedUnits0);
                    boolean hasLoggingCalls1 = hasLoggingCalls(guardedUnits1);
                    LineNumberTag lineNumberTag = (LineNumberTag)unit.getTag("LineNumberTag");
                    if (hasLoggingCalls0 && hasLoggingCalls1) {
                        LOGGER.warn("Both branch of logging guard {} at line {} have logging calls! Need manual examination!",
                                unit.toString(), lineNumberTag==null?"Unknown":lineNumberTag.getLineNumber());
                        Set<Unit> result = new HashSet<>();
                        result.addAll(guardedUnits0);
                        result.addAll(guardedUnits1);
                        guardedStmts.put(unit, result);
                    } else if (!hasLoggingCalls0 && !hasLoggingCalls1) {
                        LOGGER.warn("Both branch of logging guard {} at line {} don't have logging calls! Need manual examination!",
                                unit.toString(), lineNumberTag==null?"Unknown":lineNumberTag.getLineNumber());
                        Set<Unit> result = new HashSet<>();
                        result.addAll(guardedUnits0);
                        result.addAll(guardedUnits1);
                        guardedStmts.put(unit, result);
                    } else if (hasLoggingCalls0) {
                        guardedStmts.put(unit, guardedUnits0);
                    } else {
                        guardedStmts.put(unit, guardedUnits1);
                    }

                } else if (backEdges.contains(unit)) {
                    // 3.2. if the common node is EXIT node and the guard IfStmt is back edge,
                    // which means one succ of the IfStmt is loop header
                    Stmt target = ((IfStmt) unit).getTarget();
                    if (succ0 == target) {
                        guardedStmts.put(unit, reachable1);
                    } else {
                        guardedStmts.put(unit, reachable0);
                    }
                } else {
                    // 3.2. if the common node is EXIT node and the guard IfStmt is back edge,
                    // which means at least one of the branch stop early
                    // (ends with return / throw) and doesn't merge with another branch.
                    LineNumberTag lineNumberTag = (LineNumberTag)unit.getTag("LineNumberTag");
                    LOGGER.warn("Both branch of logging guard {} at line {} don't merge! Need manual examination!",
                            unit.toString(), lineNumberTag==null?"Unknown":lineNumberTag.getLineNumber());
                    Set<Unit> result = new HashSet<>();
                    result.addAll(reachable0);
                    result.addAll(reachable1);
                    guardedStmts.put(unit, result);
                }
            }
        }

        in.union(gen, out);
        out.difference(kill);
    }

    @Override
    protected FlowSet newInitialFlow() {
        return emptySet.clone();
    }

    @Override
    protected void merge(FlowSet in1, FlowSet in2, FlowSet out) {
        in1.union(in2, out);
    }

    @Override
    protected void copy(FlowSet source, FlowSet dest) {
        source.copy(dest);
    }


    private boolean hasLoggingCalls(Collection<Unit> units) {
        for (Unit unit : units) {
            if (unit instanceof  Stmt && ((Stmt) unit).containsInvokeExpr()) {
                SootMethod method = ((Stmt) unit).getInvokeExpr().getMethod();
                String methodName = method.getName();
                String className = method.getDeclaringClass().getName();
                String qualifiedMethodName = String.join(".", className, methodName);
                for (String loggingMethod : loggingMethodSigs) {
                    if (qualifiedMethodName.startsWith(loggingMethod)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


}
