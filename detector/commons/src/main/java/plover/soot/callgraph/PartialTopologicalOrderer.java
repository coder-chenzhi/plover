package plover.soot.callgraph;

import soot.Scene;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Targets;
import soot.jimple.toolkits.callgraph.TopologicalOrderer;
import soot.util.NumberedSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class PartialTopologicalOrderer extends TopologicalOrderer {
	private Collection<SootMethod> _entries;
	CallGraph _cg;
	List<SootMethod> _order = new ArrayList<SootMethod>();
    NumberedSet _visited = new NumberedSet( Scene.v().getMethodNumberer() );

	public PartialTopologicalOrderer(CallGraph cg, Collection<SootMethod> entries) {
		super(cg);

		this._entries = new ArrayList<SootMethod>(entries);
		this._cg = cg;
	}

	public void go() {
		Iterator<SootMethod> methods = _entries.iterator();
		while (methods.hasNext()) {
			SootMethod m = (SootMethod) methods.next();
			dfsVisit(m);
		}
	}
	
	@SuppressWarnings("rawtypes")
	private void dfsVisit( SootMethod m ) {
        if( _visited.contains( m ) ) return;
        _visited.add( m );
        Iterator targets = new Targets(_cg.edgesOutOf(m) );
        while( targets.hasNext() ) {
            SootMethod target = (SootMethod) targets.next();
            dfsVisit( target );
        }
        _order.add( m );
    }
	
	public List<SootMethod> order() { return _order; }
}
