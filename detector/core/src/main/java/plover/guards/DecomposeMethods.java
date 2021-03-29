package plover.guards;

import plover.utils.SootExecutorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;

public class DecomposeMethods {

    public static final Logger LOGGER = LoggerFactory.getLogger(DecomposeMethods.class);

    private static List<String> skipMethods = new ArrayList<>();

    private static int visitedMethodsUpper = 100;

    private int visitedMethodsCount = 0;

    static {
        skipMethods.add("isDebugEnabled");
        skipMethods.add("isTraceEnabled");
        skipMethods.add("void debug");
        skipMethods.add("void trace");
        skipMethods.add("com.ctc.wstx");
//        skipMethods.add("com.google.common");
        skipMethods.add("org.apache.log4j");
//        skipMethods.add("org.apache.commons");
        skipMethods.add("org.slf4j");
//        skipMethods.add("org.apache.zookeeper");
        skipMethods.add("logTraceMessage");
        skipMethods.add("logRequest");
        skipMethods.add("logQuorumPacket");
        skipMethods.add("logToCSV");
        skipMethods.add("dumpOpCounts");
    }

    CallGraph callGraph;
    Map<SootMethod, Set<SootMethod>> knownMethodsToJDKCallee;

    public DecomposeMethods(CallGraph callGraph) {
        this.callGraph = callGraph;
        knownMethodsToJDKCallee = new HashMap<>();
    }

    /**
     * for each method:
     *  1. get method body
     *  2. iterate statements in method body
     *      2.1 if it is method invocation
     *          2.2.1 if the callee is JDK method, store it
     *          2.2.2 if the callee is not JDK method, go into the method // how to handle multiple callee?
     *
     * @param method
     */
    public void decompose(SootMethod method) {

    }

    public Set<SootMethod> getAllCalledJDKMethods(SootMethod method, List<SootMethod> visiting) {
        Set<SootMethod> JDKCallee = new HashSet<>();
        if (visitedMethodsCount > visitedMethodsUpper) {
            JDKCallee.add(method);
            LOGGER.debug("Reach upper limit of visited method: {}", visitedMethodsUpper);
            LOGGER.debug("Stop to analyze callee {} from top caller {}...", method, visiting.get(0));
        } else {
            LOGGER.debug("Start to analyze {} ...", method);
            if (method.hasActiveBody()) {
                for (Unit unit : method.getActiveBody().getUnits()) {
                    JDKCallee.addAll(getAllCalledJDKMethods(unit, visiting));
                }
            }
            visitedMethodsCount += 1;
            LOGGER.debug("Finish analyzing {} ...", method);
        }
        return JDKCallee;
    }

    public Set<SootMethod> getAllCalledJDKMethods(Unit unit, List<SootMethod> visiting) {
        if (visiting.size() == 0) {
            visitedMethodsCount = 0;
        }
        Set<SootMethod> JDKCallee = new HashSet<>();
        if (unit instanceof Stmt && ((Stmt) unit).containsInvokeExpr()) {
            for (Iterator<Edge> it = callGraph.edgesOutOf(unit); it.hasNext(); ) {
                Edge edge = it.next();
                SootMethod callee = edge.tgt();

                if (visiting.contains(callee)) {
                    continue;
                }
                
                boolean isSkip = false;
                for (String skipMethod : skipMethods) {
                    if (callee.getSignature().contains(skipMethod)) {
                        isSkip = true;
                        break;
                    }
                }

                if (isSkip) {
//                    JDKCallee.add(callee);
                    continue;
                }

                if (SootExecutorUtil.isLibraryMethod(callee)) {
                    JDKCallee.add(callee);
                } else {
                    visiting.add(callee);
                    JDKCallee.addAll(getAllCalledJDKMethods(callee, visiting));
                    visiting.remove(callee);
                }
            }
        }
        return JDKCallee;
    }

    public static void main(String[] args) {

    }

}
