package plover.filter;

import java.util.Collection;

/**
 * Class names based filter, only the classes with specific class names will be analyzed
 */
public class EqualsBasedClassFilter implements ClassFilter {

    private Collection<String> targetClassNames;

    public EqualsBasedClassFilter(Collection<String> targetClassNames) {
        this.targetClassNames = targetClassNames;
    }

    public Collection<String> getTargetClassNames() {
        return targetClassNames;
    }

    @Override
    public boolean shouldAnalyze(String className) {
        return targetClassNames.contains(className);
    }
}
