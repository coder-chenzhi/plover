package plover.sootex.du;

import plover.sootex.location.AccessPath;
import plover.sootex.location.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Unit;

import java.util.*;

public class DefUseChain {


    private IReachingDUQuery rdAnalysis;
    private IReachingDUQuery ruAnalysis;
    private List<Unit> reverseTopoOrder;
    /* definition of use, key is use unit, value is its definition units */
    private HashMap<Unit, List<Unit>> udChain;
    /* use of definition, key is definition unit, value is its use units */
    private HashMap<Unit, List<Unit>> duChain;

    private static final Logger LOGGER = LoggerFactory.getLogger(DefUseChain.class);

    public DefUseChain(IReachingDUQuery rdAnalysis, IReachingDUQuery ruAnalysis, List<Unit> reverseTopoOrder) {
        this.rdAnalysis = rdAnalysis;
        this.ruAnalysis = ruAnalysis;
        this.reverseTopoOrder = reverseTopoOrder;
        udChain = new HashMap<>(reverseTopoOrder.size());
        duChain = new HashMap<>(reverseTopoOrder.size());
    }

    public void initialization() {
        for (Unit unit : this.reverseTopoOrder) {
            Collection<Location> usedLocations = ruAnalysis.getDULocations(unit);
            Collection<Unit> defs = rdAnalysis.getReachingDUSites(unit, null, usedLocations);
            LOGGER.trace("UsedLocations {} for unit {}", usedLocations, unit);
            LOGGER.trace("Defs {} for UsedLocations {}", defs, usedLocations);
            // remove self-dependent
            // FIXME some self-dependent is reasonable, such as statements in loop
            defs.remove(unit);
            udChain.put(unit, new ArrayList<>(defs));
            for (Unit def : defs) {
                if (duChain.containsKey(def)) {
                    duChain.get(def).add(unit);
                } else {
                    duChain.put(def, new ArrayList<>(Collections.singletonList(unit)));
                }
            }
        }
    }

    /**
     * get all definitions of a use
     * @param unit
     * @return
     */
    public List<Unit> getDefUnitsOfUse(Unit unit) {
        return udChain.get(unit);
    }

    /**
     * get all uses of a definition
     * @param unit
     * @return
     */
    public List<Unit> getUseUnitsOfDef(Unit unit) {
        return duChain.get(unit);
    }

    public Collection<AccessPath> getDefAccessPaths(Unit unit) {
        return rdAnalysis.getDUAccessPath(unit);
    }

    public Collection<AccessPath> getUseAccessPaths(Unit unit) {
        return ruAnalysis.getDUAccessPath(unit);
    }

}
