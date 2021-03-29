package plover.filter;

import java.util.Collection;

/**
 * Prefix based filter, only the classes with specific package names will be analyzed
 */
public class PrefixBasedClassFilter implements ClassFilter {

    private Collection<String> targetPackageNames;

    public PrefixBasedClassFilter(Collection<String> targetPackageNames) {
        this.targetPackageNames = targetPackageNames;
    }

    public Collection<String> getTargetPackageNames() {
        return targetPackageNames;
    }

    @Override
    public boolean shouldAnalyze(String className) {
        boolean matches = false;
        for (String packageName : targetPackageNames) {
            if (className.startsWith(packageName)) {
                matches = true;
                break;
            }
        }
        return matches;
    }
}
