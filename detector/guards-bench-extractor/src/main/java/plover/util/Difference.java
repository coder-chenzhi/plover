package plover.util;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;

public class Difference {

    public static void main(String[] args) throws Exception{
        String sootResultPath = "sootResult.txt";
        String jdtResultPath = "jdtResult.txt";
        List<String> sootResultRaw = FileUtils.readLines(new File(sootResultPath), Charset.forName("UTF-8"));
        List<String> jdtResultRaw = FileUtils.readLines(new File(jdtResultPath), Charset.forName("UTF-8"));
        HashMap<String, List<Integer>> sootResult = new HashMap<>();
        HashMap<String, List<Integer>> jdtResult = new HashMap<>();
        for (String line : sootResultRaw) {
            String[] items = line.split("\t");
            String className = items[0].substring(0, items[0].indexOf(":"));
            if (className.contains("$")) {
                className = className.substring(0, className.indexOf("$"));
            }
            String lineNum = items[1];
            if (sootResult.containsKey(className)) {
                sootResult.get(className).add(Integer.parseInt(lineNum));
            } else {
                sootResult.put(className, new ArrayList<>(Arrays.asList(Integer.parseInt(lineNum))));
            }
        }

        for (String line : jdtResultRaw) {
            String[] items = line.split("\t");
            String className = items[0].replace("\\", ".").replace("/", ".").replace(".java", "");
            String lineNum = items[1];
            if (jdtResult.containsKey(className)) {
                jdtResult.get(className).add(Integer.parseInt(lineNum));
            } else {
                jdtResult.put(className, new ArrayList<>(Arrays.asList(Integer.parseInt(lineNum))));
            }
        }

        System.out.println("The items found in jdt, but not in soot: ======>");
        difference(jdtResult, sootResult);

        System.out.println("The items found in soot, but not in jdt: ======>");
        difference(sootResult, jdtResult);

    }

    /**
     * items found in <code>map1</code>, but not in <code>map2</code>
     * @param map1
     * @param map2
     */
    private static void difference(Map<String, List<Integer>> map1, Map<String, List<Integer>> map2) {
        for (Map.Entry<String, List<Integer>> item : map1.entrySet()) {
            String className = item.getKey();
            if (!map2.containsKey(className)) {
                for (Integer lineNum : item.getValue()) {
                    System.out.println(className + "\t" + lineNum);
                }
            } else {
                List<Integer> lineNums1 = item.getValue();
                List<Integer> lineNums2 = map2.get(className);
                for (Integer lineNum : lineNums1) {
                    if (!lineNums2.contains(lineNum)) {
                        System.out.println(className + "\t" + lineNum);
                    }
                }
            }
        }
    }

}
