package plover.sootex.sideeffect;

import soot.Local;
import soot.SootMethod;

import java.util.Set;

public interface ILocalityQuery {
	/**
	 * if v is local for m, which means the lifecycle of v is same with the lifecycle of m.
	 * In other word, v is created inside m and is released when m returns.
	 * @param m
	 * @param v
	 * @return
	 */
	public boolean isRefTgtLocal(SootMethod m, Local v);
	public boolean isRefTgtFresh(SootMethod m, Local v);
	public boolean isRefTgtEscape(SootMethod m, Local v);
	public boolean isRefTgtRealEscape(SootMethod m, Local v);
	public Set<Local> getLocalityLocals(SootMethod m);
	public Set<Local> getEscapedLocals(SootMethod m);
	public Set<Local> getRealEscapedLocals(SootMethod m);
}
