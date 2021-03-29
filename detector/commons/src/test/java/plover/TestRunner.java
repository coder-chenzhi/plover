package plover;

import plover.utils.RunConfig;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class TestRunner {

    public static void testGetPackageNames() {
        RunConfig runConfig = new RunConfig(true);
        Set<String> packages = runConfig.getAllPackagesFromSource("zookeeper");
        System.out.println("ORIGIN: " + packages);
        System.out.println("AFTER COMPACT: " + RunConfig.compactPackageNames(packages));
    }

    public static void testRegex() {
        String s = "package     org.apache.zookeeper.server.V1;";
        Pattern packageNamePattern = Pattern.compile("^package\\s+\\S+;");
        Matcher matcher = packageNamePattern.matcher(s.trim());
        if (matcher.matches()) {
            System.out.println(matcher.group().replaceFirst("package", "").replaceFirst(";", "").replaceFirst("\\s+", ""));
        }
    }

    public static void testSort() {
        List<String> list = new ArrayList<>(Arrays.asList(new String[]{"aa", "ab", "cd", "c", "abc"}));
        Collections.sort(list);
        System.out.println(list);
    }

    public static void testCompactPackageNames() {
        List<String> list = new ArrayList<>(Arrays.asList(new String[]{"aa", "ab", "cd", "c", "abc"}));
        System.out.println(RunConfig.compactPackageNames(list));
    }

    public static void main(String[] args) {
        testGetPackageNames();
//        testSort();
//        testCompactPackageNames();
    }

}
