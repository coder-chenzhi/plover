package plover.guards;

import com.google.common.collect.Ordering;
import plover.soot.callgraph.SimpleCallGraphFilter;
import plover.sootex.du.DUBuilder;
import plover.sootex.du.IReachingDUQuery;
import plover.sootex.ptsto.IPtsToQuery;
import plover.sootex.ptsto.SparkPtsToQuery;
import plover.sootex.sideeffect.FastEscapeAnalysis;
import plover.sootex.sideeffect.MustAliasIdentityLocalsQuery;
import plover.sootex.sideeffect.SideEffectAnalysis;
import plover.soot.hammock.CFGEntry;
import plover.soot.hammock.CFGExit;
import plover.soot.hammock.CFGProvider;
import plover.soot.hammock.HammockCFGProvider;
import plover.utils.Constants;
import plover.utils.RunConfig;
import plover.utils.SootExecutorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.tagkit.LineNumberTag;

import java.util.*;
import java.time.Instant;

public class MainEntry {

    public static final Logger LOGGER = LoggerFactory.getLogger(MainEntry.class);
    public static final Logger METRICS_LOGGER = LoggerFactory.getLogger("Metrics");

    public static void main(String[] args) throws Exception {
        boolean isLocal = false;
        boolean useSpark = true;
        boolean useSideEffect = true;
        boolean useControl = true;
        boolean enableMetrics = false;

        String projectName = "hadoop";
        RunConfig runConfig = new RunConfig(isLocal);
        String classpath = runConfig.getSootClassPath(projectName);
        List<String> entryPoints = runConfig.getSootEntryPointsSigs(projectName);
        // TODO for debug, uncomment this line
//        entryPoints = Arrays.asList("<org.apache.cassandra.locator.PropertyFileSnitch: void reloadConfiguration(boolean)>");
        List<String> loggingMethods = runConfig.getLoggingMethods(projectName);
        loggingMethods.addAll(Constants.DEFAULT_DEBUG_LOGGING_METHOD);

        Map<SootMethod, Metrics> method2metrics = new HashMap<>();
        Map<String, Metrics> loggingID2metrics = new HashMap<>();

        SootExecutorUtil.setDefaultSootOptions(classpath);
        SootExecutorUtil.setSootEntryPoints(entryPoints);

        IPtsToQuery ptsto = null;
        SideEffectAnalysis sideEffectAnalysis = null;

        CFGProvider cfgProvider = new HammockCFGProvider();

        if (useSpark) {
            LOGGER.info("[PERF] Start to build call graph at {}", Instant.now().toEpochMilli());
            SimpleCallGraphFilter refiner = new SimpleCallGraphFilter();
            SootExecutorUtil.doFastSparkPointsToAnalysis(new HashMap<>(), false, refiner);
            ptsto = new SparkPtsToQuery();
            LOGGER.info("[PERF] Finish to build call graph at {}", Instant.now().toEpochMilli());
        }

        CallGraph cg = Scene.v().getCallGraph();

        LOGGER.info("[PERF] Start AliasAnalysis at {}", Instant.now().toEpochMilli());
        MustAliasIdentityLocalsQuery mustAliasQuery = new MustAliasIdentityLocalsQuery();
        mustAliasQuery.build();
        LOGGER.info("[PERF] Finish AliasAnalysis at {}", Instant.now().toEpochMilli());

        LOGGER.info("[PERF] Start EscapeAnalysis at {}", Instant.now().toEpochMilli());
        FastEscapeAnalysis escapeAnalysis = new FastEscapeAnalysis(Scene.v().getCallGraph(), mustAliasQuery);
        escapeAnalysis.build();
        LOGGER.info("[PERF] Finish EscapeAnalysis at {}", Instant.now().toEpochMilli());

        if (useSideEffect) {
            LOGGER.info("[PERF] Start SideEffectAnalysis at {}", Instant.now().toEpochMilli());
            sideEffectAnalysis = new SideEffectAnalysis(ptsto, mustAliasQuery, escapeAnalysis, Scene.v().getEntryPoints(),
                    Constants.CUSTOMIZED_IO_METHOD);
            sideEffectAnalysis.build();
            LOGGER.info("[PERF] Finish SideEffectAnalysis at {}", Instant.now().toEpochMilli());

        }

        DUBuilder du = new DUBuilder(cfgProvider, ptsto, sideEffectAnalysis);


        LOGGER.info("[PERF] Start Intra-procedural analysis at {}", Instant.now().toEpochMilli());
        for (SootMethod method : Scene.v().getEntryPoints()) {
            // TODO for debug, uncomment this line
//            if (!method.getSignature().equals("<org.apache.zookeeper.util.SecurityUtils$1: javax.security.sasl.SaslClient run()>")) {
//                continue;
//            }
            IReachingDUQuery rdAnalysis = du.getGlobalDUQuery().getRDQuery(method);
            IReachingDUQuery ruAnalysis = du.getGlobalDUQuery().getRUQuery(method);

            OverheadFinder finder = new OverheadFinder(method, cfgProvider.getCFG(method),
                    rdAnalysis, ruAnalysis, escapeAnalysis, sideEffectAnalysis, loggingMethods,
                    Constants.CUSTOMIZED_IO_METHOD, useControl);

            finder.doAnalysis();
            if (finder.skippableUnits.size() > 0) {
                List<Unit> overheads = new ArrayList<>(finder.skippableUnits.keySet());
                overheads.remove(CFGEntry.v());
                overheads.remove(CFGExit.v());
                List<Unit> originalOrder = new ArrayList<>(method.getActiveBody().getUnits());
                overheads.sort(Ordering.explicit(originalOrder));
                // calc number of lines
                LineNumberTag lineNumberTag;
                List<Integer> lineNumbers = new ArrayList<>();
                for (int i = 0; i < overheads.size(); i++) {
                    lineNumberTag = (LineNumberTag)overheads.get(i).getTag("LineNumberTag");
                    lineNumbers.add(lineNumberTag==null?-1:lineNumberTag.getLineNumber());
                }

                LOGGER.info("Find overhead at method {}:{} has {} units and {} lines",
                        method.getSignature(),
                        method.getJavaSourceStartLineNumber(),
                        finder.skippableUnits.size(),
                        new HashSet<>(lineNumbers).size());

                for (int i = 0; i < overheads.size(); i++) {
                    String unitContent = overheads.get(i).toString();
                    String linNum = lineNumbers.get(i)==-1?"UNKNOWN":lineNumbers.get(i).toString();
                    Set<String> loggingIDs = finder.skippableUnits.get(overheads.get(i));

                    LOGGER.info("\t -> {} AT LINE {} with ID {}", unitContent, linNum, loggingIDs);

                    if (enableMetrics && METRICS_LOGGER.isInfoEnabled()) {
                        Unit unit = overheads.get(i);
                        for (String id : loggingIDs) {
                            if (loggingID2metrics.containsKey(id)) {
                                Metrics cur = loggingID2metrics.get(id);
                                cur.setPotentialInstruction(cur.getPotentialInstruction()+1);
                            } else {
                                Metrics tmp = new Metrics(0, 1);
                                loggingID2metrics.put(id, tmp);
                            }
                        }

                        if (unit instanceof Stmt && ((Stmt) unit).containsInvokeExpr()) {
                            for (Iterator<Edge> it = cg.edgesOutOf(unit); it.hasNext(); ) {
                                Edge edge = it.next();
                                SootMethod callee = edge.tgt();
                                int reachableMethodsCount = 0;
                                int reachableInstCount = 0;

                                if (method2metrics.containsKey(callee)) {
                                    Metrics tmp = method2metrics.get(callee);
                                    reachableMethodsCount = tmp.getPotentialMethodCall();
                                    reachableInstCount = tmp.getPotentialInstruction();
                                } else {
                                    ReachableMethods reachableMethods
                                            = new ReachableMethods(cg, Collections.<MethodOrMethodContext>singletonList(callee));
                                    reachableMethods.update();
                                    reachableMethodsCount = reachableMethods.size();
                                    for (Iterator<MethodOrMethodContext> iterator = reachableMethods.listener(); iterator.hasNext();) {
                                        SootMethod m = (SootMethod) iterator.next();
                                        if (m.hasActiveBody()){
                                            reachableInstCount += m.getActiveBody().getUnits().size();
                                        }
                                    }
                                    method2metrics.put(callee, new Metrics(reachableMethodsCount, reachableInstCount));
                                }

                                for (String id : loggingIDs) {
                                    if (loggingID2metrics.containsKey(id)) {
                                        Metrics cur = loggingID2metrics.get(id);
                                        cur.setPotentialInstruction(cur.getPotentialInstruction()+reachableInstCount);
                                        cur.setPotentialMethodCall(cur.getPotentialMethodCall()+reachableMethodsCount);
                                    } else {
                                        Metrics tmp = new Metrics(reachableMethodsCount, reachableInstCount);
                                        loggingID2metrics.put(id, tmp);
                                    }
                                }

                            }
                        }
                    }
                }



            } else {
                LOGGER.warn("No overhead is found at method {}", method.getSignature());
            }
        }

        if (enableMetrics && METRICS_LOGGER.isInfoEnabled()) {
            for (String id : loggingID2metrics.keySet()) {
                METRICS_LOGGER.info("LoggingID: {}: {}", id, loggingID2metrics.get(id));
            }
        }

        LOGGER.info("[PERF] Finish Intra-procedural analysis at {}", Instant.now().toEpochMilli());


    }
}

