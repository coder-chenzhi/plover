package plover.stats;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

enum Metrics {
    RECALL,
    PRECISION,
    SELECT_TOP_METHODS, // TOP 1%
    SELECT_RANDOM_METHODS
}

public class Calc {

    private static final Logger LOGGER = LoggerFactory.getLogger(Calc.class);

    public static void measurement(Metrics metrics) throws IOException {
        String projectName = "Hive";
        boolean useControl = true;
        boolean useSideEffect = true;

        String benchFile = MessageFormat.format(
                "/home/user/Code/plover/conf/guard-bench/{0}_guard_bench.txt", projectName);
        String ourFile = MessageFormat.format(
                "/home/user/Code/plover/docs/result/{0}_Data_{1}Control_{2}SideEffect_Raw_Result.txt",
                projectName, useControl?"":"No", useSideEffect?"":"No");
        List<String> benchRawResult = FileUtils.readLines(new File(benchFile));
        List<String> ourRawResult = FileUtils.readLines(new File(ourFile));
        Map<String, List<String>> benchResult = new HashMap<>();
        Map<String, List<String>> ourResult = new HashMap<>();
        String methodName;
        String methodPattern;
        String contentPattern;
        boolean debug = true;

        methodName = "Unknown";
        methodPattern = "Find guard at method ";
        contentPattern = " -> ";
        for (Iterator<String> it = benchRawResult.iterator(); it.hasNext();) {
            String line = it.next();
            if (line.contains(methodPattern)) {
                line = line.substring(line.indexOf(methodPattern) + methodPattern.length());
                methodName = line.substring(0, line.lastIndexOf(" line "));
                if (!benchResult.containsKey(methodName)) {
                    benchResult.put(methodName, new ArrayList<>());
                }
            } else if (line.contains(contentPattern)) {
                String unit = line.substring(line.indexOf(contentPattern) + contentPattern.length());
                if (unit.contains(" with ID")) {
                    unit = unit.substring(0, unit.lastIndexOf(" with ID"));
                }
                benchResult.get(methodName).add(unit.replaceAll("tmp\\$[0-9]+", "tmp"));
            }
        }

        methodName = "Unknown";
        methodPattern = "Find overhead at method ";
        contentPattern = " -> ";
        for (Iterator<String> it = ourRawResult.iterator(); it.hasNext();) {
            String line = it.next();
            if (line.contains(methodPattern)) {
                line = line.substring(line.indexOf(methodPattern) + methodPattern.length());
                methodName = line.substring(0, line.lastIndexOf(":"));
                if (!ourResult.containsKey(methodName)) {
                    ourResult.put(methodName, new ArrayList<>());
                }
            } else if (line.contains(contentPattern)) {
                String unit = line.substring(line.indexOf(contentPattern) + contentPattern.length());
                if (unit.contains(" with ID")) {
                    unit = unit.substring(0, unit.lastIndexOf(" with ID"));
                }
                ourResult.get(methodName).add(unit.replaceAll("tmp\\$[0-9]+", "tmp"));
            }
        }

        if (metrics.equals(Metrics.RECALL)) {
            LOGGER.info("start to calc Recall:");

            int matchedBenchUnitCount = 0;
            int totalBenchUnitCount = 0;

            for (Map.Entry<String, List<String>> entry : benchResult.entrySet()) {
                String method = entry.getKey();
                totalBenchUnitCount += entry.getValue().size();
                if (ourResult.containsKey(method)) {
                    if (debug) {
                        System.out.println("Start to analyze method: " + method);
                    }
                    List<String> ourValue = ourResult.get(method);
                    for (String unit : entry.getValue()) {
                        if (ourValue.contains(unit)) {
                            matchedBenchUnitCount++;
                        } else {
                            if (debug) {
                                System.out.println("Unit: " + unit + " is not found in our analysis.");
                            }
                        }
                    }
                } else {
                    if (debug) {
                        System.out.println("All overhead in " + method + " are not found in our analysis!");
                    }
                }
            }
            System.out.println("Total units in benchmark: " + totalBenchUnitCount);
            System.out.println("Matched units in our result: " + matchedBenchUnitCount);
        } else if (metrics.equals(Metrics.PRECISION)) {
            LOGGER.info("start to calc Precision:");

            int matchedBenchUnitCount = 0;
            int totalOurResultUnitCount = 0;

            for (Map.Entry<String, List<String>> entry : ourResult.entrySet()) {
                String method = entry.getKey();
                totalOurResultUnitCount += entry.getValue().size();
                if (benchResult.containsKey(method)) {
                    if (debug) {
                        System.out.println("Start to analyze method: " + method);
                    }
                    List<String> benchValue = benchResult.get(method);
                    for (String unit : entry.getValue()) {
                        if (benchValue.contains(unit)) {
                            matchedBenchUnitCount++;
                        } else {
                            if (debug) {
                                System.out.println("Unit: " + unit + " is not found in bench analysis.");
                            }
                        }
                    }
                } else {
                    if (debug) {
                        System.out.println("All overhead in " + method + " are not found in bench analysis!");
                    }
                }
            }
            System.out.println("Total units in our results: " + totalOurResultUnitCount);
            System.out.println("Matched units in bench result: " + matchedBenchUnitCount);
        } else if (metrics.equals(Metrics.SELECT_TOP_METHODS)) {
            System.out.println("Select top 1% method with most overhead in our result");
            int size = ourResult.size() / 100 * 1;
            List sortedKeys = ourResult.entrySet().stream().
                    sorted(Comparator.comparingInt(e -> -e.getValue().size())).
                    collect(Collectors.toList());
            for (int i = 0; i < size; i++) {
                Map.Entry<String, List<String>> ele = (Map.Entry<String, List<String>>) sortedKeys.get(i);
                System.out.println("Find overhead at method " + ele.getKey() + " with " + ele.getValue().size() + " Units");
                for (String line : ele.getValue()) {
                    System.out.println(" -> " + line);
                }
            }
        } else if (metrics.equals(Metrics.SELECT_RANDOM_METHODS)) {
            System.out.println("Select random 1% method with most overhead in our result");
            int size = ourResult.size() / 100 * 1;
            List<String> keyLists = new ArrayList<>(ourResult.keySet());
            Collections.shuffle(keyLists);
            for (int i = 0; i < size; i++) {
                System.out.println("Find overhead at method " + keyLists.get(i) +
                        " with " + ourResult.get(keyLists.get(i)).size() + " Units");
                for (String line : ourResult.get(keyLists.get(i))) {
                    System.out.println(" -> " + line);
                }
            }
        }
    }


    public static void compare() throws IOException {
        String projectName = "Hadoop";

        String resultFile1 = MessageFormat.format(
                "/home/user/Code/plover/docs/result/{0}_Data_{1}Control_{2}SideEffect_Raw_Result.txt",
                projectName, false?"":"No", false?"":"No");
        String resultFile2 = MessageFormat.format(
                "/home/user/Code/plover/docs/result/{0}_Data_{1}Control_{2}SideEffect_Raw_Result.txt",
                projectName, true?"":"No", true?"":"No");

        List<String> rawResult1 = FileUtils.readLines(new File(resultFile1));
        List<String> rawResult2 = FileUtils.readLines(new File(resultFile2));
        Map<String, List<String>> ourResult1 = new HashMap<>();
        Map<String, List<String>> ourResult2 = new HashMap<>();
        String methodName;
        String methodPattern;
        String contentPattern;
        boolean debug = true;

        methodName = "Unknown";
        methodPattern = "Find overhead at method ";
        contentPattern = " -> ";

        for (Iterator<String> it = rawResult1.iterator(); it.hasNext();) {
            String line = it.next();
            if (line.contains(methodPattern)) {
                line = line.substring(line.indexOf(methodPattern) + methodPattern.length());
                methodName = line.substring(0, line.lastIndexOf(" has "));
                if (!ourResult1.containsKey(methodName)) {
                    ourResult1.put(methodName, new ArrayList<>());
                }
            } else if (line.contains(contentPattern)) {
                String unit = line.substring(line.indexOf(contentPattern) + contentPattern.length());
                if (unit.contains(" with ID")) {
                    unit = unit.substring(0, unit.lastIndexOf(" with ID"));
                }
                ourResult1.get(methodName).add(unit);
            }
        }

        for (Iterator<String> it = rawResult2.iterator(); it.hasNext();) {
            String line = it.next();
            if (line.contains(methodPattern)) {
                line = line.substring(line.indexOf(methodPattern) + methodPattern.length());
                methodName = line.substring(0, line.lastIndexOf(" has "));
                if (!ourResult2.containsKey(methodName)) {
                    ourResult2.put(methodName, new ArrayList<>());
                }
            } else if (line.contains(contentPattern)) {
                String unit = line.substring(line.indexOf(contentPattern) + contentPattern.length());
                if (unit.contains(" with ID")) {
                    unit = unit.substring(0, unit.lastIndexOf(" with ID"));
                }
                ourResult2.get(methodName).add(unit);
            }
        }


        for (Map.Entry<String, List<String>> result1Entry : ourResult1.entrySet()) {
            String method = result1Entry.getKey();
            if (ourResult2.containsKey(method)) {
                if (debug) {
                    System.out.println("Start to analyze method: " + method);
                }
                List<String> result2Value = ourResult2.get(method);
                for (String unit : result1Entry.getValue()) {
                    if (result2Value.contains(unit)) {

                    } else {
                        if (debug) {
                            System.out.println("Unit: " + unit + " is not found in " + resultFile2);
                        }
                    }
                }
            } else {
                if (debug) {
                    System.out.println("All overhead in " + method + " are not found in " + resultFile2);
                }
            }
        }

    }

    public static void main(String[] args) throws IOException {
        measurement(Metrics.RECALL);
//        compare();
    }
}
