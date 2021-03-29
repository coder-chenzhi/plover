package plover.utils;

import soot.Unit;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.DominatorNode;
import soot.toolkits.graph.DominatorTree;

import java.util.*;

public class DorminatorUtil {

    public static Set<DominatorNode> findAncestors(DominatorNode start, DominatorNode end, boolean includeEnd){
        Set<DominatorNode> ancestors = new HashSet<DominatorNode>();
        DominatorNode parent = start.getParent();
        while(parent!=null && parent!=end && !ancestors.contains(parent)){
            ancestors.add(parent);
            parent = parent.getParent();
        }

        if(parent!=null && includeEnd){
            ancestors.add(parent);
        }

        return ancestors;
    }


    /**
     * find first common post-dominator of <code>n1</code> and <code>n2</code>
     * @param n1
     * @param n2
     * @return
     */
    public static DominatorNode findFirstCommonPostDominator(DominatorTree<Unit> postDomTree, DominatorNode n1, DominatorNode n2) {
        // early check may improve performance
        if (postDomTree.isDominatorOf(n1, n2)) {
            return n1;
        } else if (postDomTree.isDominatorOf(n2, n1)) {
            return n2;
        }
        Collection<DominatorNode> ancestors2 = findAncestors(n2, null, true);
        ancestors2.add(n2);
        DominatorNode parent = n1;
        while(parent!=null){
            if(ancestors2.contains(parent)){
                return parent;
            }

            parent = parent.getParent();
        }

        return null;
    }

    /**
     * Get all units between <code>start</code> and <code>end</code>.
     * The <code>start</code> have to be post-dominated by <code>end</code>, or this function doesn't make sense.
     * @param start
     * @param end
     * @return
     */
    public static List<Unit> getAllUnitsBetween(DirectedGraph<Unit> graph, Unit start, Unit end) {
        List<Unit> visited = new ArrayList<>();
        if (start == end) {
            return visited;
        }
        visitGraph(graph, start, end, visited);
        return visited;
    }

    public static void visitGraph(DirectedGraph<Unit> graph, Unit start, Unit end, List<Unit> visited) {
        visited.add(start);
        List<Unit> succs = graph.getSuccsOf(start);
        for (Unit succ : succs) {
            if (succ != end && !visited.contains(succ)) {
                visitGraph(graph, succ, end, visited);
            }
        }
    }


}
