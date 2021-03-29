package plover.utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunConfig {

    /**
     * key is name of project
     * value is a list, including:
     *   a file of all entry points,
     *   a file of class path,
     *   the folder of source code,
     *   a file of user-defined guard methods
     *   a file of user-defined logging methods
     *
     */
    public HashMap<String, List<String>> params = new HashMap<>();

    public HashMap<String, String> projectsRoot = new HashMap<>();

    public RunConfig(boolean isLocal) {
        if (isLocal) {
            
        } else {
            params.put("hadoop",
                    Arrays.asList("/home/user/Code/plover/conf/log-entries/hadoop_log_entries.txt",
                            "/home/user/Code/plover/conf/classpaths/hadoop_classpath.txt",
                            "/home/user/Data/hadoop-2.9.2-src",
                            "/home/user/Code/plover/conf/guard-methods/hadoop_guard_methods.txt",
                            "/home/user/Code/plover/conf/logging-methods/hadoop_logging_methods.txt"));
            params.put("cassandra",
                    Arrays.asList("/home/user/Code/plover/conf/log-entries/cassandra_log_entries.txt",
                            "/home/user/Code/plover/conf/classpaths/cassandra_classpath.txt",
                            "/home/user/Data/cassandra-3.11.3-src"));
            params.put("zookeeper",
                    Arrays.asList("/home/user/Code/plover/conf/log-entries/zookeeper_log_entries.txt",
                            "/home/user/Code/plover/conf/classpaths/zookeeper_classpath.txt",
                            "/home/user/Data/zookeeper-3.4.13",
                            "/home/user/Code/plover/conf/guard-methods/zookeeper_guard_methods.txt",
                            "/home/user/Code/plover/conf/logging-methods/zookeeper_logging_methods.txt"));
            params.put("hbase",
                    Arrays.asList("/home/user/Code/plover/conf/log-entries/hbase_log_entries.txt",
                            "/home/user/Code/plover/conf/classpaths/hbase_classpath.txt",
                            "/home/user/Data/hbase-2.1.1-src/hbase-2.1.1",
                            "/home/user/Code/plover/conf/guard-methods/hbase_guard_methods.txt",
                            "/home/user/Code/plover/conf/logging-methods/hbase_logging_methods.txt"));
            params.put("hive",
                    Arrays.asList("/home/user/Code/plover/conf/log-entries/hive_log_entries.txt",
                            "/home/user/Code/plover/conf/classpaths/hive_classpath.txt",
                            "/home/user/Data/apache-hive-3.1.1-src",
                            "/home/user/Code/plover/conf/guard-methods/hive_guard_methods.txt",
                            "/home/user/Code/plover/conf/logging-methods/hive_logging_methods.txt"));

            projectsRoot.put("{cassandra_root}", "/home/user/Data/cassandra-3.11.3-bin");
            projectsRoot.put("{hadoop_root}", "/home/user/Data/hadoop-2.9.2-bin");
            projectsRoot.put("{zookeeper_root}", "/home/user/Data/zookeeper-3.4.13");
            projectsRoot.put("{hbase_root}", "/home/user/Data/hbase-2.1.1-bin/hbase-2.1.1");
            projectsRoot.put("{hive_root}", "/home/user/Data/apache-hive-3.1.1-bin");
            projectsRoot.put("{JAVA_HOME}", "/usr/lib/jvm/java-8-openjdk-amd64");
        }

    }

    public String getSootClassPath(String projectName) throws Exception {
        if (!params.containsKey(projectName)) {
            throw new Exception(String.format("Cannot find configs for project: {%s}. " +
                    "Please check the config in plover.utils.RunConfig.java and re-run mvn install in the utils module.", projectName));
        } else if (params.get(projectName).size() < 2) {
            throw new Exception(String.format("Cannot find config of classpath file for project: {%s}. " +
                    "Please check the config in plover.utils.RunConfig.java and re-run mvn install in the utils module.", projectName));
        } else {
            String classPathConfigFile = params.get(projectName).get(1);

            File classFile = new File(classPathConfigFile);
            String fileContent = FileUtils.readFileToString(classFile);
            for (String key : projectsRoot.keySet()) {
                fileContent = fileContent.replace(key, projectsRoot.get(key));
            }
            String classPath = fileContent.replace("\r\n", File.pathSeparator)
                    .replace("\r", File.pathSeparator).replace("\n", File.pathSeparator);

            return classPath;
        }
    }

    /**
     * get the signatures of entry points
     * @param projectName
     * @return
     * @throws IOException
     */
    public List<String> getSootEntryPointsSigs(String projectName) throws Exception {
        if (!params.containsKey(projectName)) {
            throw new Exception(String.format("Cannot find configs for project: {%s}. " +
                    "Please check the config in plover.utils.RunConfig.java and re-run mvn install in the utils module.", projectName));
        } else if (params.get(projectName).size() < 1) {
            throw new Exception(String.format("Cannot find config of entry-points file for project: {%s}. " +
                    "Please check the config in plover.utils.RunConfig.java and re-run mvn install in the utils module.", projectName));
        } else {
            String entryConfigFilename = params.get(projectName).get(0);
            File entryFile = new File(entryConfigFilename);
            List<String> entries = FileUtils.readLines(entryFile);
            return entries;
        }
    }

    /**
     * get the user-defined guard methods
     * @param projectName
     * @return if not find settings, return empty list
     * @throws IOException
     */
    public List<String> getGuardMethods(String projectName) throws Exception {
        if (!params.containsKey(projectName)) {
            System.err.println(String.format("Cannot find configs for project: {%s}. " +
                    "Please check the config in plover.utils.RunConfig.java and re-run mvn install in the utils module.", projectName));
            return new ArrayList<>();
        } else if (params.get(projectName).size() < 4) {
            System.err.println(String.format("Cannot find config of guard-methods file for project: {%s}, just use default guard methods. " +
                    "Please check the config in plover.utils.RunConfig.java and re-run mvn install in the utils module.", projectName));
            return new ArrayList<>();
        } else {
            String guardConfigFilename = params.get(projectName).get(3);
            File guardConfigFile = new File(guardConfigFilename);
            List<String> methods = FileUtils.readLines(guardConfigFile);
            return methods;
        }
    }

    /**
     * get the user-defined logging methods
     * @param projectName
     * @return
     * @throws IOException
     */
    public List<String> getLoggingMethods(String projectName) throws Exception {
        if (!params.containsKey(projectName)) {
            System.err.println(String.format("Cannot find configs for project: {%s}. " +
                    "Please check the config in plover.utils.RunConfig.java and re-run mvn install in the utils module.", projectName));
            return new ArrayList<>();
        } else if (params.get(projectName).size() < 5) {
            System.err.println(String.format("Cannot find config of logging-methods file for project: {%s}, just use default guard methods. " +
                    "Please check the config in plover.utils.RunConfig.java and re-run mvn install in the utils module.", projectName));
            return new ArrayList<>();
        } else {
            String guardConfigFilename = params.get(projectName).get(4);
            File guardConfigFile = new File(guardConfigFilename);
            List<String> methods = FileUtils.readLines(guardConfigFile);
            return methods;
        }
    }


    /**
     * get all package names from .java files
     * @param projectName name of project
     * @return
     */
    public Set<String> getAllPackagesFromSource(String projectName) {
        // equivalent to unix command: grep -Er '^package\s+\S+;' [folder]
        File srcFolder = new File(params.get(projectName).get(2));
        return getAllPackagesFromSource(srcFolder);
    }

    /**
     * get all package names from .java files
     * @param srcFolder full path of a directory or a text file contains the full paths of all source files
     * @return
     */
    public static Set<String> getAllPackagesFromSource(File srcFolder) {
        Pattern packageNamePattern = Pattern.compile("^package\\s+\\S+;");
        Set<String> packageNames = new HashSet<>();
        Collection<File> allFiles = new ArrayList<>();
        // source folder given, only .java source file will be analyzed
        if (srcFolder.isDirectory()) {
            allFiles = FileUtils.listFiles(srcFolder, new String[]{"java"}, true);
        } else {
            // a file contains all the full paths of source files
            try {
                List<String> lines = FileUtils.readLines(srcFolder);
                for (String line : lines) {
                    File path = new File(line);
                    if (path.isDirectory()) {
                        allFiles.addAll(FileUtils.listFiles(path, new String[]{"java"}, true));
                    } else if (line.endsWith(".java")) {
                        allFiles.add(path);
                    } else {
                        System.err.println(line + " is not a .java or a directory");
                    }
                }
            } catch (IOException e) {
                System.err.println("Can not read file " + srcFolder.toString());
            }
        }

        for (File file : allFiles) {
            try {
                List<String> lines = FileUtils.readLines(file);
                for (String line : lines) {
                    Matcher matcher = packageNamePattern.matcher(line.trim());
                    if (matcher.matches()) {
                        packageNames.add(matcher.group().replaceFirst("package", "").replaceFirst(";", "").replaceFirst("\\s+", ""));
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("Can not read file " + file.toString());
            }
        }

        return packageNames;
    }

    public static Set<String> compactPackageNames(Collection<String> original) {
        Set<String> compacted = new HashSet<>();
        List<String> sorted = new ArrayList<String>(original);
        Collections.sort(sorted);

        while (!sorted.isEmpty()) {
            boolean hasPrefix = false;
            String cur = sorted.get(sorted.size()-1);
            for (int i = sorted.size() - 2; i >= 0; i--) {
                if (cur.startsWith(sorted.get(i))) {
                    hasPrefix = true;
                    break;
                }
            }
            if (!hasPrefix) {
                compacted.add(cur);
            }
            sorted.remove(cur);
        }
        return compacted;
    }


}
