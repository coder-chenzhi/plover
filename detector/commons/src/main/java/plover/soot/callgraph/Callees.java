package plover.soot.callgraph;

import soot.Kind;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 *
 */
public class Callees{	
	protected Set<SootMethod> _all;
	protected Set<SootMethod> _explicitCallees;
	protected Set<SootMethod> _implicitCallees;
	protected Set<SootMethod> _threadCallees;
	
	public Callees(CallGraph cg, SootMethod caller){
		collect(cg.edgesOutOf(caller));
	}
	
	public Callees(CallGraph cg, Unit callsite){
		collect(cg.edgesOutOf(callsite));
	}
	
	private void collect(Iterator<Edge> it){
		_all = new HashSet<SootMethod>(10);
		_explicitCallees = new HashSet<SootMethod>();
		_implicitCallees = new HashSet<SootMethod>();
		_threadCallees = new HashSet<SootMethod>();
		
		while(it.hasNext()){
			Edge edge = (Edge)it.next();
			SootMethod tgt = edge.tgt();
			
			_all.add(tgt);
			if(edge.isExplicit()){
				_explicitCallees.add(tgt);
			}
			else {
				_implicitCallees.add(tgt);
			}
			
			if(edge.kind()== Kind.THREAD){
				_threadCallees.add(tgt);
			}
		}
	}	
	
	public Set<SootMethod> explicits(){
		return _explicitCallees;
	}
	
	protected Set<SootMethod> implicits(){
		return _implicitCallees;
	}
	
	public Set<SootMethod> threads(){
		return _threadCallees;
	}
	
	public Set<SootMethod> all(){
		return _all;
	}
}
