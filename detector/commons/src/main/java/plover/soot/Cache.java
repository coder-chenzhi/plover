package plover.soot;

import plover.soot.callgraph.DirectedCallGraph;
import soot.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.PseudoTopologicalOrderer;

import java.util.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class Cache {
	private static Cache _instance = new Cache();
	private Cache(){
		Global.v().regesiterResetableGlobals(Cache.class);
	}
	public static Cache v(){		
		return _instance;
	}
    
	private Map<SootClass,Set> _class2fields = new HashMap<SootClass,Set>();
	private List _topoOrder;
	private List _reverseTopoOrder;
	private DirectedGraph<List<SootMethod>> _componentCallGraph;

	 /** 
     * Get the possible instance fields of a class, including fields declared in the class
     * definition and fields of super classes.
     */
	public Set<SootField> getAllInstanceFields(SootClass cls){
    	Set fields = _class2fields.get(cls);
    	if(fields==null){
    		fields = SootUtils.findAllInstanceFields(cls);
    		_class2fields.put(cls, fields);    		
    	}
        
        return fields;
    }
    
	public List<MethodOrMethodContext> getTopologicalOrder() {
		if(_topoOrder==null){
			if(Scene.v().hasCallGraph()){
				Collection entries = Scene.v().getEntryPoints();
				CallGraph cg = Scene.v().getCallGraph();
				PseudoTopologicalOrderer pto = new PseudoTopologicalOrderer();
				DirectedGraph methodCallGraph = new DirectedCallGraph(cg, entries);
				_topoOrder = pto.newList(methodCallGraph, false);
			}
			else{
				_topoOrder = Collections.EMPTY_LIST;
			}
		}		 
		
		return _topoOrder;
	}

	public List<MethodOrMethodContext> getReverseTopologicalOrder() {
		if(_reverseTopoOrder==null){
			if(Scene.v().hasCallGraph()){
				Collection entries = Scene.v().getEntryPoints();
				CallGraph cg = Scene.v().getCallGraph();
				PseudoTopologicalOrderer pto = new PseudoTopologicalOrderer();
				DirectedCallGraph dcg = SootUtils.getDirectedCallGraph(cg, entries);
				_reverseTopoOrder = pto.newList(dcg, true);
			}
			else{
				_reverseTopoOrder = Collections.EMPTY_LIST;
			}
		}

		return _reverseTopoOrder;
	}

	public DirectedGraph<List<SootMethod>> getComponentCallGraph() {
		return _componentCallGraph;
	}

	public void setComponentCallGraph(DirectedGraph<List<SootMethod>> cg) {
		this._componentCallGraph = cg;
	}
    
    /** For analysis reset. */
    public static void reset(){
    	_instance = new Cache();
    }
}
