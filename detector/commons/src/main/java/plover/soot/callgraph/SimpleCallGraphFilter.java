package plover.soot.callgraph;

import plover.soot.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;

/**
 * filter out non-explicit edges
 * filter out all edges to java.lang.Object
 */
public class SimpleCallGraphFilter extends CallGraphRefiner {

    public static final Logger LOGGER = LoggerFactory.getLogger(SimpleCallGraphFilter.class);

    public static String[] PACKAGE = {
            "java.lang.Object",
            "java.lang.Throwable",
            "org.eclipse.jdt.internal.compiler"
    };

    public static SootClass[] SUPER_TYPE = {
//            Scene.v().getSootClass("java.util.Collection")
    };

    public SimpleCallGraphFilter() {
        super(Scene.v().getPointsToAnalysis());
    }

    // only consider explicit calls
    public boolean isEdgeIgnored(Edge edge) {
        if (edge.isExplicit()) {
            String className;
            SootClass sootClass;
            sootClass = edge.getTgt().method().getDeclaringClass();
            className = sootClass.getName();
            for (String typeName : PACKAGE) {
                if (className.startsWith(typeName)) {
                    return true;
                }
            }
            if (SUPER_TYPE.length > 0 && isSubType(sootClass)) {
                return true;
            }
            Stmt stmt = edge.srcStmt();
            if (stmt.containsInvokeExpr()) {
                sootClass = stmt.getInvokeExpr().getMethod().getDeclaringClass();
                className = sootClass.getName();
                for (String typeName : PACKAGE) {
                    if (className.startsWith(typeName)) {
                        return true;
                    }
                }
                if (SUPER_TYPE.length > 0 && isSubType(sootClass)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /**
     * Refine context-insensitive call graph, heuristically ignore many methods
     * to reduce the analysis cost.
     */
    public CallGraph refine(CallGraph cg){
        Date startTime = new Date();
        int oldSize = cg.size();

        //selectively copy to a new call graph
        CallGraph newCg = new CallGraph();
        Stack<SootMethod> stack = new Stack<SootMethod>();
        stack.addAll(Scene.v().getEntryPoints());
        Set<SootMethod> processed = new HashSet<SootMethod>();

        while(!stack.isEmpty()){
            SootMethod m = (SootMethod)stack.pop();

            if(!processed.add(m)){
                continue; //if already processed
            }

            for(Iterator<Edge> it = cg.edgesOutOf(m); it.hasNext();){
                Edge e = it.next();
                SootMethod tgt = e.tgt();

                if(isEdgeIgnored(e)){
                    continue;
                }

                newCg.addEdge(e);

                if(!processed.contains(tgt)){
                    stack.add(tgt);
                }
            }
        }

        Date endTime = new Date();
        int newSize = newCg.size();
        LOGGER.info("[Call Graph] Refine call graph in {} , new call graph has {}  edges (original : {})",
                Utils.getTimeConsumed(startTime, endTime), newSize, oldSize);

        return newCg;
    }

    private static boolean isSubType(SootClass target) {
        List<SootClass> superTypeList = new ArrayList<>();
        SootClass object = Scene.v().getSootClass("java.lang.Object");
        if (target == object) {
            // object is not the sub type of any types
            return false;
        }
        SootClass superType = target.getSuperclass();

        while (superType != null && superType != object) {
            superTypeList.add(superType);
            superType = superType.getSuperclass();
        }
        superTypeList.addAll(new ArrayList<>(target.getInterfaces()));

        Stack<SootClass> workingList = new Stack<>();
        List<SootClass> visited = new ArrayList<>();
        workingList.addAll(superTypeList);
        while (!workingList.isEmpty()) {
            SootClass candidate = workingList.pop();
            if (!visited.contains(candidate)) {
                visited.add(candidate);
                if (candidate.getInterfaceCount() > 0) {
                    for (SootClass superInterface : candidate.getInterfaces()) {
                        if (!visited.contains(superInterface)) {
                            workingList.add(superInterface);
                            superTypeList.add(superInterface);
                        }
                    }
                }
            }
        }

        for (SootClass targetSuperType : SUPER_TYPE) {
            if (superTypeList.contains(targetSuperType)) {
                return true;
            }
        }
        return false;
    }

}