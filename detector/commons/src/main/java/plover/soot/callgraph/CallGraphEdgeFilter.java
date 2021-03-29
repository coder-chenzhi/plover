package plover.soot.callgraph;

import soot.jimple.toolkits.callgraph.Edge;

public class CallGraphEdgeFilter{
	public boolean isIgnored(Edge e){
		return false;
	}
}