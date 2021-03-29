package plover.filter;

import java.util.Collection;

public interface ClassFilter {

    /**
     * if specified class should be analyzed
     * @param className
     * @return
     */
    boolean shouldAnalyze(String className);

}


