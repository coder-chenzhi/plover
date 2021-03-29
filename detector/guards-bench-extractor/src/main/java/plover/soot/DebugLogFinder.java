package plover.soot;

import plover.utils.Constants;
import plover.utils.RunConfig;
import plover.utils.SootExecutorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.Stmt;
import soot.tagkit.LineNumberTag;

import java.util.*;

public class DebugLogFinder {

    private static Logger LOGGER = LoggerFactory.getLogger(SootMainEntry.class);

    /* methods are skipped */
    public static String[] SKIP_METHODS = new String[]{
            "<org.apache.cassandra.cql3.Cql_Parser: void <clinit>()>"
    };

    public static void main(String[] args) throws Exception {
        Boolean isLocal = false;
        String projectName = "hive";
        RunConfig runConfig = new RunConfig(isLocal);
        String classpath = runConfig.getSootClassPath(projectName);
        List<String> entryPoints = runConfig.getSootEntryPointsSigs(projectName);
        List<String> guardMethods = runConfig.getGuardMethods(projectName);
        guardMethods.addAll(Constants.DEFAULT_DEBUG_GUARD_METHOD_SIGNATURE);
        List<String> loggingMethods = runConfig.getLoggingMethods(projectName);
        loggingMethods.addAll(Constants.DEFAULT_DEBUG_LOGGING_METHOD);

        SootExecutorUtil.setDefaultSootOptions(classpath);
        SootExecutorUtil.setSootEntryPoints(entryPoints);

        for (SootMethod method : Scene.v().getEntryPoints()) {
            Set<Integer> lines = new HashSet<>();
            method.retrieveActiveBody();
            Collection<Unit> units = method.getActiveBody().getUnits();
            for (Unit unit : units) {
                if (unit instanceof Stmt && ((Stmt) unit).containsInvokeExpr()) {
                    SootMethod invokedMethod = ((Stmt) unit).getInvokeExpr().getMethod();
                    String methodName = invokedMethod.getName();
                    String className = invokedMethod.getDeclaringClass().getName();
                    String qualifiedMethodName = String.join(".", className, methodName);
                    for (String loggingMethod : loggingMethods) {
                        if (qualifiedMethodName.startsWith(loggingMethod)) {
                            LineNumberTag lineNumberTag = (LineNumberTag)unit.getTag("LineNumberTag");
                            int lineNum = lineNumberTag.getLineNumber();
                            if (!lines.contains(lineNum)) {
                                lines.add(lineNum);
                                LOGGER.info("Find debug log at method {} line {} is {}",
                                        method.getSignature(),
                                        lineNum,
                                        unit.toString());
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
}
