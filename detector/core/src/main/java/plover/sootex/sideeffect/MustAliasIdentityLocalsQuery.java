package plover.sootex.sideeffect;

import plover.soot.Cache;
import plover.soot.SootUtils;
import plover.soot.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;
import soot.toolkits.graph.BriefUnitGraph;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class MustAliasIdentityLocalsQuery {

    public static final Logger LOGGER = LoggerFactory.getLogger(MustAliasIdentityLocalsQuery.class);

    private MustAliasIdentityLocalsAnalysis[] method2MustAlias;

    public MustAliasIdentityLocalsQuery() {
        this.method2MustAlias = new MustAliasIdentityLocalsAnalysis[SootUtils.getMethodCount()];
    }

    public void build() {
        LOGGER.info("[AliasAnalysis] must alias to identity locals analysis ...");
        Date startBuild = new Date();
        List<?> rm = Cache.v().getReverseTopologicalOrder();
        for (Iterator<?> it = rm.iterator(); it.hasNext();) {
            SootMethod sootMethod = (SootMethod) it.next();
            if (sootMethod.isConcrete()) {
                // alias analysis
                LOGGER.trace("[AliasAnalysis] alias analysis for {}: {}",
                        sootMethod.getSignature(), sootMethod.getNumber());
                if (method2MustAlias[sootMethod.getNumber()] == null) {
                    method2MustAlias[sootMethod.getNumber()] =
                            new MustAliasIdentityLocalsAnalysis(new BriefUnitGraph(sootMethod.getActiveBody()));
                }
            }
        }
        Date endBuild = new Date();
        LOGGER.info("[AliasAnalysis] complete in  {}", Utils.getTimeConsumed(startBuild,endBuild));
    }

    public MustAliasIdentityLocalsAnalysis query(SootMethod method) {
        return method2MustAlias[method.getNumber()];
    }

}
