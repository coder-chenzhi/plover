package plover.soot.callgraph;

import soot.MethodOrMethodContext;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.ReachableMethods;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Collect caller methods. support filters
 *
 */
public class Callers{	
	protected Set<MethodOrMethodContext> _all;
	
	public static interface Filter{
		public boolean want(SootMethod m);
	}
	
	private static class NoFilter implements Filter{
		public boolean want(SootMethod m){
			return true;
		}
	}
	
	private static class SetFilter implements Filter{
		public Set<MethodOrMethodContext> _set;
		
		public SetFilter(Set<MethodOrMethodContext> mask){
			this._set = mask;
		}
		public boolean want(SootMethod m){
			return _set.contains(m);
		}
	}
	
	private static class ReachableFilter implements Filter{
		public ReachableMethods _rm;
		
		public ReachableFilter(ReachableMethods rm){
			this._rm = rm;
		}
		public boolean want(SootMethod m){
			return _rm.contains(m);
		}
	}
	
	private static NoFilter NOFILTER = new NoFilter();
	
	public Callers(CallGraph cg, MethodOrMethodContext callee, Filter filter){
		build(cg,callee,filter);		
	}	
	
	public Callers(CallGraph cg, MethodOrMethodContext callee){
		build(cg,callee,NOFILTER);
	}

	//@param maskSet     Only collect callers in this set
	public Callers(CallGraph cg, MethodOrMethodContext callee, Set<MethodOrMethodContext> maskSet){
		Filter filter = new SetFilter(maskSet);		
		build(cg,callee,filter); 
	}
	
	public Callers(CallGraph cg, MethodOrMethodContext callee, ReachableMethods rm){
		Filter filter = new ReachableFilter(rm);		
		build(cg,callee,filter); 
	}	
	
	void build(CallGraph cg, MethodOrMethodContext callee, Filter filter){
		_all = new HashSet<MethodOrMethodContext>();
		Iterator<Edge> it = cg.edgesInto(callee);
		while(it.hasNext()){
			Edge edge = it.next();
			SootMethod src = edge.src();
			
			if(filter.want(src))
				_all.add(src);		
		}
	}
	
	public Set<MethodOrMethodContext> all(){
		return _all;
	}
}
