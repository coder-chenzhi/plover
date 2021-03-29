package plover.sootex.location;

import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.Constant;
import soot.jimple.ReturnStmt;
import soot.toolkits.graph.DirectedGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * In Jimple, a method body may have more than one return. MethodRet unify all these returns
 */
public class MethodRet extends Location {
	protected final SootMethod _method;
	protected Collection<Location> _valueSources;
	
	MethodRet(SootMethod method) {
		this._method = method;
		
		Collection<Unit> units = method.getActiveBody().getUnits();
		_valueSources = new ArrayList<Location>(10);		
		findValueSource(units, _valueSources);
	}

	public String toString() {
		return _method.getName() + ".ret";
	}

	public Type getType() {
		return _method.getReturnType();
	}

	public SootMethod getMethod() {
		return _method;
	}

	private void findValueSource(Collection<Unit> units, Collection<Location> result) {
		for (Unit u: units) {
			if (u instanceof ReturnStmt){
				Value val = ((ReturnStmt)u).getOp();
				if (!(val instanceof Constant)) {
					Location loc = Location.valueToLocation(val);
					result.add(loc);
				}
			}
		}
	}
	
	public Collection<Location> getValueSource(DirectedGraph<Unit> cfg) {
		List<Unit> tails = cfg.getTails();
		Collection<Location> result = new ArrayList<Location>(tails.size());
		findValueSource(tails, result);		
		return result;
	}
	
	public Collection<Location> getValueSource() {
		return _valueSources;
	}
}