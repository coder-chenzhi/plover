package plover.filter;

public class NeverClassFilter implements ClassFilter {

    @Override
    public boolean shouldAnalyze(String className) {
        return true;
    }
}
