package plover.soot.graph;
import soot.toolkits.graph.DirectedGraph;

import java.util.*;

/**
 *
 */
public class GraphHelper {
	public static <T> Set<T> getReachables(DirectedGraph<T> graph, T start){
		return getReachables(graph, start,null);
	}

	public static <T> Set<T> getReachables(DirectedGraph<T> graph, T start, T block){
		Collection<T> starts = new ArrayList<T>(1);
		starts.add(start);
		Collection<T> blocks = new ArrayList<T>(1);
		blocks.add(block);
		return getReachables(graph, starts,blocks);
	}

	
	public static <T> Set<T> getReachables(DirectedGraph<T> graph, Collection<T> starts, Collection<T> block) {
		Set<T> results = new HashSet<T>();
		Stack<T> stack = new Stack<T>();
		Set<T> inStack = new HashSet<T>();

		stack.addAll(starts);
		inStack.addAll(starts);

		while (!stack.isEmpty()) {
			T top = stack.pop();
			inStack.remove(top);

			//if meet a block node, can not pass it
			if(block!=null && block.contains(top)){
				continue;
			}
			
			if (!results.add(top)) {
				continue;
			}

			List<T> nexts = graph.getSuccsOf(top);
			if (nexts == null)
				continue;

			for (Iterator<T> it = nexts.iterator(); it.hasNext();) {
				T succ = it.next();
				if (!results.contains(succ) && !inStack.contains(succ)) { 
					stack.add(succ);
					inStack.add(succ);
				}
			}
		}

		return results;
	}

	/**
	 * breadth first traverse graph
	 * @param graph graph
	 * @param start start node, if is null, use the heads or tails of the graph
	 * @param forwards direction of traverse, forwards or backwards
	 * @param <N>
	 * @return
	 */
    public static <N> Collection<N> breadthFirstTraverse(DirectedGraph<N> graph, N start, boolean forwards) {
		LinkedList<N> workingList = new LinkedList<>();
		Set<N> visited = new HashSet<>();

		if (start == null) {
			if (forwards) {
				workingList.addAll(graph.getHeads());
			} else {
				workingList.addAll(graph.getTails());
			}
		} else {
			workingList.add(start);
		}

		while (!workingList.isEmpty()) {
			N node = workingList.removeFirst();
			if (visited.contains(node)) {
				continue;
			}
			visited.add(node);
			List<N> next;
			if (forwards) {
				next = graph.getSuccsOf(node);
			} else {
				next = graph.getPredsOf(node);
			}
			workingList.addAll(next);
		}
		return visited;
	}
}
