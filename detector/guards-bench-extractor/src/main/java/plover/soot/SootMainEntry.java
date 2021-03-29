package plover.soot;

import com.google.common.collect.Ordering;
import plover.filter.ClassFilter;
import plover.filter.PrefixBasedClassFilter;
import plover.soot.callgraph.SimpleCallGraphFilter;
import plover.soot.hammock.CFGEntry;
import plover.soot.hammock.CFGExit;
import plover.soot.hammock.HammockCFG;
import plover.utils.Constants;
import plover.utils.RunConfig;
import plover.utils.SootExecutorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.tagkit.LineNumberTag;
import soot.util.Chain;

import java.util.*;

public class SootMainEntry {

    private static Logger LOGGER = LoggerFactory.getLogger(SootMainEntry.class);

    /* methods are skipped */
    public static String[] SKIP_METHODS = new String[]{
            "<org.apache.cassandra.cql3.Cql_Parser: void <clinit>()>"
    };

    public static void main(String[] args) throws Exception {
        Boolean isLocal = false;
        Boolean useReachableMethods = true;
        String projectName = "hive";
        RunConfig runConfig = new RunConfig(isLocal);
        String classpath = runConfig.getSootClassPath(projectName);
        List<String> entryPoints = runConfig.getSootEntryPointsSigs(projectName);
        List<String> guardMethods = runConfig.getGuardMethods(projectName);
        guardMethods.addAll(Constants.DEFAULT_DEBUG_GUARD_METHOD_SIGNATURE);
        List<String> loggingMethods = runConfig.getLoggingMethods(projectName);
        loggingMethods.addAll(Constants.DEFAULT_DEBUG_LOGGING_METHOD);
        ClassFilter packageFilter = new PrefixBasedClassFilter(
                RunConfig.compactPackageNames(runConfig.getAllPackagesFromSource(projectName)));
        ClassFilter hbaseShadedFilter = new PrefixBasedClassFilter(Arrays.asList("org.apache.hadoop.hbase.shaded",
                "org.apache.hadoop.metrics2.lib.MutableMetricsFactory"));

        ClassFilter hiveShadedFilter = new PrefixBasedClassFilter(Arrays.asList("org.apache.hive.druid.io",
                "org.apache.hive.druid.com", "org.apache.hive.druid.org", "org.apache.hadoop.mapreduce",
                "org.apache.hadoop.mapred", "org.apache.hadoop.fs", "org.apache.hadoop.security"));

        SootExecutorUtil.setDefaultSootOptions(classpath);
        SootExecutorUtil.setSootEntryPoints(entryPoints);

        if (useReachableMethods) {
            SimpleCallGraphFilter refiner = new SimpleCallGraphFilter();
            CHATransformer.v().transform();
            CallGraph oldCG = Scene.v().getCallGraph();
            CallGraph newCG = refiner.refine(oldCG);
            Scene.v().setCallGraph(newCG);
            Scene.v().setReachableMethods(null);
            ReachableMethods reachables = Scene.v().getReachableMethods();
            for (Iterator<?> it = reachables.listener(); it.hasNext(); ) {
                SootMethod method = (SootMethod) it.next();
                if (packageFilter.shouldAnalyze(method.getDeclaringClass().getName()) && method.isConcrete()) {
                    if ("hbase".equals(projectName) && hbaseShadedFilter.shouldAnalyze(method.getDeclaringClass().getName())) {
                        continue;
                    }
                    if ("hive".equals(projectName) && hiveShadedFilter.shouldAnalyze(method.getDeclaringClass().getName())) {
                        continue;
                    }
                    analyzeMethod(method, loggingMethods, guardMethods);
                }
            }
        } else {
            Chain<SootClass> classes = Scene.v().getClasses();
            List<SootClass> classLists = new ArrayList<>();
            classLists.addAll(classes);
            for (SootClass clazz : classLists) {
                if (packageFilter.shouldAnalyze(clazz.getName())) {
                    for (SootMethod method : clazz.getMethods()) {
                        if (method.isConcrete()) {
                            analyzeMethod(method, loggingMethods, guardMethods);
                        }
                    }
                }
            }
        }
    }

    private static void analyzeMethod(SootMethod method, List<String> loggingMethods, List<String> guardMethods) {
        // TODO for debug, uncomment this line
//        if (!"<org.apache.cassandra.gms.Gossiper: void applyStateLocally(java.util.Map)>".equals(method.getSignature())) {
//            return;
//        }
        // skip method
        for (String skip : SKIP_METHODS) {
            if (skip.equals(method.getSignature())) {
                return;
            }
        }

        LOGGER.info("Start to analyze {}", method.getSignature());

        Body body = method.retrieveActiveBody();
        HammockCFG cfg = new HammockCFG(body);
        GuardFinder finder = new GuardFinder(cfg, loggingMethods, guardMethods);
        HashMap<Unit, Set<Unit>> stmts = finder.getGuardedStmts();
        for (Map.Entry<Unit, Set<Unit>> entry : stmts.entrySet()) {
            LineNumberTag lineNumberTag;
            Unit guard = entry.getKey();
            List<Unit> guardedStmts = new ArrayList<>(entry.getValue());
            guardedStmts.remove(CFGEntry.v());
            guardedStmts.remove(CFGExit.v());
            List<Unit> originalOrder = new ArrayList<>(method.getActiveBody().getUnits());
            guardedStmts.sort(Ordering.explicit(originalOrder));

            // calc number of lines
            List<Integer> lineNumbers = new ArrayList<>();
            for (int i = 0; i < guardedStmts.size(); i++) {
                lineNumberTag = (LineNumberTag)guardedStmts.get(i).getTag("LineNumberTag");
                lineNumbers.add(lineNumberTag==null?-1:lineNumberTag.getLineNumber());
            }

            lineNumberTag = (LineNumberTag)guard.getTag("LineNumberTag");
            LOGGER.info("Find guard at method {} line {} has {} units and {} lines",
                    method.getSignature(),
                    lineNumberTag==null?"UNKNOWN":lineNumberTag.getLineNumber(),
                    guardedStmts.size(),
                    new HashSet<>(lineNumbers).size());
            LOGGER.info("GUARD: {} AT LINE {}",
                    guard.toString(),
                    lineNumberTag==null?"UNKNOWN":lineNumberTag.getLineNumber());

            for (int i = 0; i < guardedStmts.size(); i++) {
                LOGGER.info("\t -> {} AT LINE {}", guardedStmts.get(i).toString(),
                        lineNumbers.get(i)==-1?"UNKNOWN":lineNumbers.get(i));
            }
        }
    }

}
