package plover.sootex.sideeffect;

import plover.sootex.location.AccessPath;
import soot.*;
import soot.jimple.Stmt;

import java.util.Collection;

/**
 * Collect the locations a program entity can access.
 * We only track the side-effect on heap locations of specific non-local objects (this object and formal parameters),
 * which is used to build dependencies between statements of caller. And we use lazy AccessPath resolving here.
 * More specifically, the side-effects are represented as AccessPath during propagation,
 * and they will be resolved to actual location only when we reach the target methods.
 * In our analysis, the target methods are those method involving logging method calls.
 * Besides, we also identify unskippable side-effects,
 * which is used to determine whether the method invocation can be skipped.
 */
public interface ISideEffectAnalysis {  

    /**
     * get modified heap location of local object
     * @param method
     * @return
     */
    public Collection<AccessPath> getModHeapLocs(SootMethod method);

    /**
     * get used heap location of local object
     * @param method
     * @return
     */
    public Collection<AccessPath> getUseHeapLocs(SootMethod method);

    /**
     * if the method has unskippbale side-effect, including
     *   modifying static field,
     *   modifying instance field of non-local object,
     *   performing I/O
     *   calling other methods with unskippable side-effect
     * @param method
     * @return
     */
    public boolean hasUnskippableSideEffect(SootMethod method);

    public Collection<AccessPath> getMappingAccessPath(Stmt stmt, SootMethod callee, Collection<AccessPath> calleeAP);

}
