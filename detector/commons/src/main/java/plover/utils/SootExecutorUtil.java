package plover.utils;

import plover.soot.SootUtils;
import plover.soot.Utils;
import plover.soot.callgraph.CallGraphRefiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.options.Options;

import java.util.*;

public class SootExecutorUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(SootExecutorUtil.class);

    public static void setDefaultSootOptions(String classPath) {
        // Prepend classpath
        Options.v().set_soot_classpath(classPath);
        Options.v().set_prepend_classpath(true);

        // Enable whole-program mode to enable cg, wjtp, wjop and wjap packs
        Options.v().set_whole_program(true);
        // Enable application mode
        // When Soot is in application mode, every class referenced from the argument classes, directly or indirectly,
        // is also an application class, unless its package name indicates that it is part of the standard Java runtime system
        Options.v().set_app(true);

        // Keep line number
        Options.v().set_keep_line_number(true);

        // Do not load class body for excluded classes
        Options.v().set_no_bodies_for_excluded(false);

        // Allow unresolved classes; may not get active bodies of methods and cause errors
        Options.v().set_allow_phantom_refs(true);

        // class output
        Options.v().set_output_format(Options.output_format_none);

        // Use Original Names
        PhaseOptions.v().setPhaseOption("jb", "use-original-names:true");

        // Don't reuse local variables
        // Note that turning off this option may ease the analysis of def-use, but increase the number of local variables
//        PhaseOptions.v().setPhaseOption("jb.ulp", "enabled:false");

        // Skip the generation of baf body generation
        PhaseOptions.v().setPhaseOption("bb", "enabled:false");
    }

    public static void closeOptimization() {
        PhaseOptions.v().setPhaseOption("jop.ubf1", "enabled:false");
        PhaseOptions.v().setPhaseOption("jop.ubf2", "enabled:false");
    }

    /**
     * call <code>setDefaultSootOptions(String classPath) before invoking this method</code>
     */
    public static void setSootEntryPoints(List<String> entryPointsSigs) {
        ArrayList<SootMethod> entryPoints = new ArrayList<SootMethod>();
        for (String entry : entryPointsSigs) {
            String cname = Scene.v().signatureToClass(entry);
            String mname = Scene.v().signatureToSubsignature(entry);
            SootClass sootClass = null;
            try {
                sootClass = Scene.v().loadClass(cname, SootClass.BODIES);
            } catch (Exception e) {
                LOGGER.warn("Can not get find {}", cname, e);
            }

            if (sootClass != null) {
                try {
                    SootMethod method = sootClass.getMethod(mname);
                    entryPoints.add(method);
                } catch (Exception e) {
                    LOGGER.warn("Can not find method {}\n", entry, e);
                }
            }

        }
        Scene.v().setEntryPoints(entryPoints);
        Scene.v().loadNecessaryClasses();
    }

    public static void doFastSparkPointsToAnalysis(Map<String,String> opt, boolean usePoem, CallGraphRefiner refiner) {
        opt.put("simulate-natives","false");
        opt.put("implicit-entry","false");
        if (usePoem) {
            SootUtils.doGeomPointsToAnalysis(opt);
        } else {
            SootUtils.doSparkPointsToAnalysis(opt);

        }
        if (refiner != null) {
            // simplify call graph, ignore method not reachable from main entry
            // ignore implicit calls (except thread calls)
            CallGraph cg = Scene.v().getCallGraph();
            PointsToAnalysis ptsTo = Scene.v().getPointsToAnalysis();
            CallGraph newCg = refiner.refine(cg);
            Scene.v().setCallGraph(newCg);
            Scene.v().setReachableMethods(null);   //update reachable methods
        }
    }

    public static void doSparkPointsToAnalysis(Map<String,String> opt) {
        LOGGER.info("[Spark] Starting analysis ...");
        Date startBuild = new Date();

        HashMap<String,String> defaultOptions = new HashMap<String,String>();
        defaultOptions.put("enabled","true");
        defaultOptions.put("verbose","true");
        defaultOptions.put("ignore-types","false");
        defaultOptions.put("force-gc","false");
        defaultOptions.put("pre-jimplify","false");
        defaultOptions.put("vta","false");
        defaultOptions.put("rta","false");
        defaultOptions.put("field-based","false");
        defaultOptions.put("types-for-sites","false");
        defaultOptions.put("string-constants","false");
        defaultOptions.put("simple-edges-bidirectional","false");
        defaultOptions.put("on-fly-cg","true");
        defaultOptions.put("simplify-offline","false");
        defaultOptions.put("simplify-sccs","false");
        defaultOptions.put("ignore-types-for-sccs","false");
        defaultOptions.put("propagator","worklist"); // default is auto
        defaultOptions.put("set-impl","double");
        defaultOptions.put("double-set-old","hybrid");
        defaultOptions.put("double-set-new","hybrid");
        defaultOptions.put("dump-html","false");
        defaultOptions.put("dump-pag","false");
        defaultOptions.put("dump-solution","false");
        defaultOptions.put("topo-sort","false");
        defaultOptions.put("dump-types","true");
        defaultOptions.put("class-method-var","true");
        defaultOptions.put("dump-answer","false");
        defaultOptions.put("add-tags","false");
        defaultOptions.put("set-mass","false");
        defaultOptions.put("trim-clinit","true");
        defaultOptions.put("all-reachable","false");

        // default is true, it will group all StringBuffer and StringBuilder as one single allocation site
        // we need to close this to guarantee the right allocation site
        defaultOptions.put("merge-stringbuffer","false");

        // Set the following configurations to false may reduce safety,
        // but dramatically improve performance
        defaultOptions.put("simulate-natives","true");       // false to increase speed
        defaultOptions.put("implicit-entry","true");         // false to ignore implicit entries

        if (opt != null) {
            for(Map.Entry<String, String> e: opt.entrySet()){
                String name = e.getKey();
                String value = e.getValue();
                defaultOptions.put(name, value);
            }
        }

        SparkTransformer.v().transform("",defaultOptions);

        Date endBuild = new Date();
        LOGGER.info("[Spark] complete in  {}", Utils.getTimeConsumed(startBuild,endBuild));
    }

    public static void doGeomPointsToAnalysis(Map<String,String> opt) {
        LOGGER.info("[Geom] Starting analysis ...");
        Date startBuild = new Date();

        HashMap<String,String> defaultOptions = new HashMap<String,String>();
        defaultOptions.put("enabled","true");
        defaultOptions.put("verbose","true");
        defaultOptions.put("ignore-types","false");
        defaultOptions.put("force-gc","false");
        defaultOptions.put("pre-jimplify","false");
        defaultOptions.put("vta","false");
        defaultOptions.put("rta","false");
        defaultOptions.put("field-based","false");
        defaultOptions.put("types-for-sites","false");
        defaultOptions.put("merge-stringbuffer","false");
        defaultOptions.put("string-constants","false");
        defaultOptions.put("simple-edges-bidirectional","false");
        defaultOptions.put("on-fly-cg","true");
        defaultOptions.put("simplify-offline","false");      // true
        defaultOptions.put("simplify-sccs","false");
        defaultOptions.put("ignore-types-for-sccs","false");
        defaultOptions.put("propagator","worklist");
        defaultOptions.put("set-impl","double");
        defaultOptions.put("double-set-old","hybrid");
        defaultOptions.put("double-set-new","hybrid");
        defaultOptions.put("dump-html","false");
        defaultOptions.put("dump-pag","false");
        defaultOptions.put("dump-solution","false");
        defaultOptions.put("topo-sort","false");
        defaultOptions.put("dump-types","true");
        defaultOptions.put("class-method-var","true");
        defaultOptions.put("dump-answer","false");
        defaultOptions.put("add-tags","false");
        defaultOptions.put("set-mass","false");
        defaultOptions.put("trim-clinit","true");
        defaultOptions.put("all-reachable","false");
        defaultOptions.put("geom-pta", "true");
        defaultOptions.put("geom-encoding", "geom");
        defaultOptions.put("geom-worklist", "PQ");
        defaultOptions.put("geom-eval", "1");
        defaultOptions.put("geom-trans", "false");
        defaultOptions.put("geom-frac-base", "40");
        defaultOptions.put("geom-blocking", "false");
        defaultOptions.put("geom-runs", "1");
        defaultOptions.put("geom-app-only", "false");

        // Set the following configurations to false may reduce safety,
        // but dramatically improve performance
        defaultOptions.put("simulate-natives","true");       //false to increase speed
        defaultOptions.put("implicit-entry","true");         //false to ignore implicit entries

        if (opt != null) {
            for(Map.Entry<String, String> e: opt.entrySet()){
                String name = e.getKey();
                String value = e.getValue();
                defaultOptions.put(name, value);
            }
        }

        SparkTransformer.v().transform("",defaultOptions);


        Date endBuild = new Date();
        LOGGER.info("[Geom] complete in  {}", Utils.getTimeConsumed(startBuild,endBuild));
    }

    /**
     * Sometimes we need to know which class is a JDK class. There is no simple way to distinguish a user class and a JDK
     * class, here we use the package prefix as the heuristic.
     *
     */
    public static boolean isLibraryMethod(SootMethod method) {
        return method.isJavaLibraryMethod();
    }




}
