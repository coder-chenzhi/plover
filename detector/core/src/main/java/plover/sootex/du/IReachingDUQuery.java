package plover.sootex.du;

import java.util.*;

import plover.sootex.location.AccessPath;
import plover.sootex.location.Location;
import soot.*;


/**
 * Reaching def/use query
 */
public interface IReachingDUQuery {
    /**
     * get the Location format DEF/USE of <code>Unit u</code>
     * @param u
     * @return
     */
	public Collection<Location> getDULocations(Unit u);

    /**
     * get the AccessPath format of DEF/USE of <code>Unit u</code>
     * workaround for no use of points-to analysis
     * @param u
     * @return
     */
	public Collection<AccessPath> getDUAccessPath(Unit u);
	
    /**
     * Get the reaching definition or use sites of the given location
     * @param loc  The location whose definition or use sites are queried
     * @param ap   The access path of the queried location (This parameter is optional)
     */
    public Collection<Unit> getReachingDUSites(Unit stmt, AccessPath ap, Location loc);

    /**
     * Get the reaching definition or use sites of the given locations
     * @param locs  The location whose definition or use sites are queried
     * @param ap   The access path of the queried location (This parameter is optional)
     */
    public Collection<Unit> getReachingDUSites(Unit stmt, AccessPath ap, Collection<Location> locs);
}
 