package plover.sootex.ptsto;

import java.util.*;

import plover.sootex.location.InstanceObject;
import plover.sootex.location.Location;
import soot.SootMethod;
import soot.Unit;

/**
 * A standard interface for query points-to information.
 */
public interface IPtsToQuery {
    public Set<InstanceObject> getPointTos(SootMethod m, Unit stmt, Location ptr);
}
