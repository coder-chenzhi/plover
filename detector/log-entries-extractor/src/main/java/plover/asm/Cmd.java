package plover.asm;

import plover.utils.RunConfig;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plover.filter.ClassFilter;
import plover.filter.EqualsBasedClassFilter;
import plover.filter.NeverClassFilter;
import plover.filter.PrefixBasedClassFilter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;


@Deprecated
public class Cmd {

    private static Options options = new Options();

    private static CommandLineParser parser = new DefaultParser();

    private static HelpFormatter helpFormatter = new HelpFormatter();

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd.HH.mm.ss");

    // TODO change to options, and support asterisk wildcard
    private static Set<String> loggingMethods = new HashSet<String>(
            Arrays.asList("org.slf4j.Logger.trace", "org.slf4j.Logger.debug", // slf4j
                    "org.apache.logging.log4j.Logger.trace", "org.apache.logging.log4j.Logger.debug", // Log4j 2.x
                    "org.apache.log4j.Logger.trace", "org.apache.log4j.Category.debug" // log4j 1.x
                    /* org.apache.hadoop.hdfs.server.namenode.logAuditMessage */)); // hadoop

    private final static Logger LOGGER = LoggerFactory.getLogger(Cmd.class);

    static {
        options.addOption(Option.builder("classFiles").required(true).hasArg()
                .desc("Classes files under analysis. " +
                        "You should put the full paths of classes into one .txt file. " +
                        "And each line of the file should be a .class file or .jar file. or a directory. " +
                        "If a .jar file is specified, all .class files in the jar will be analyzed. " +
                        "If a directory is specified, all .class and .jar files under the directory will be analyzed. ").build());

        options.addOption(Option.builder("classNames").required(false).hasArg()
                .desc("Full qualified class names (such as java.lang.String), which is used to filter third-party class files. " +
                        "You should put all entries into one .txt file. " +
                        "And each line of the file should be a class name." +
                        "Note:: This class names are only used to filter class files in .jar. " +
                        "Raw .class files will not be filtered. ").build());

        options.addOption(Option.builder("sourceFiles").required(false).hasArg()
                .desc("Java source files, which is used to filter third-party class files. "+
                        "You should put the full path of .java files into one .txt file. " +
                        "And each line of the file should be a .java file or a directory. " +
                        "If a directory is specified, all .java files under the directory will be analyzed. " +
                        "Note:: This source files are only used to filter class files in .jar. " +
                        "Raw .class files will not be filtered. We will extract package names from all .java file" +
                        "(such as src/main/java, src/java, and src), to extract the full qualified class name of .java file." +
                        "If this default behaviour can't meet your requirement, please use -classNames options to " +
                        "setting the class names directly. If -classNames is specified, -sourceFiles won't work. " +
                        "If both of them aren't specified, none of class files will be filtered.").build());

        options.addOption(Option.builder("outputPath").required(false).hasArg()
                .desc("Output path. Must be a directory.").build());

        options.addOption(Option.builder("threads").required(false).hasArg()
                .desc("Number of threads. \n Note::: This feature hasn't been implemented.").build());

    }

    public static void main(String[] args) {
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            helpFormatter.printHelp("Extract Log Entries", options);
            System.exit(1);
            return;
        }

        // start to process cmd

        Set<String> candidateClassFiles = new HashSet<>();
        // if relevant options are not specified, we never filter any classes
        ClassFilter classFilter = new NeverClassFilter();
        String outputPath = "result_" + LocalDateTime.now().format(dateTimeFormatter) + ".txt";
        int threadNum = 1;

        if (cmd.hasOption("classFiles")) {
            // will always be true
            List<String> entries = new ArrayList<>();
            String value = cmd.getOptionValue("classFiles");
            if (value.endsWith(".txt")) {
                try {
                    entries = Files.readAllLines(Paths.get(value));
                } catch (IOException e) {
                    LOGGER.error("Fail to read {} to get class files", value);
                }
            } else {
                // ease to test
                entries = Arrays.asList(value.split(File.pathSeparator));
            }

            for (String entry : entries) {
                File path = new File(entry);
                if (path.isDirectory()) {
                    Collection<File> allFiles = FileUtils.listFiles(path, new String[]{"class", "jar"}, true);
                    for (File file : allFiles) {
                        try {
                            candidateClassFiles.add(file.getCanonicalPath());
                        } catch (IOException e) {
                            LOGGER.trace("Can not get file path for {}", file);
                        }
                    }
                } else if (entry.endsWith(".class") | entry.endsWith(".jar")) {
                    candidateClassFiles.add(entry);
                } else {
                    LOGGER.warn("{} is not a .jar, .class or a directory", entry);
                }
            }
        }

        if (cmd.hasOption("classNames")) {
            String value = cmd.getOptionValue("classNames");
            Set<String> targetClassNames = new HashSet<>();
            if (value.endsWith(".txt")) {
                try {
                    targetClassNames = new HashSet<>(Files.readAllLines(Paths.get(value)));
                } catch (IOException e) {
                    LOGGER.warn("Fail to read {} to get source files", value);
                }
            } else {
                LOGGER.warn("You should set -classNames with the full path of a .txt file");
            }
            classFilter = new EqualsBasedClassFilter(targetClassNames);
        } else if (cmd.hasOption("sourceFiles")) {
            String value = cmd.getOptionValue("sourceFiles");
            Set<String> packages = RunConfig.getAllPackagesFromSource(new File(value));
            classFilter = new PrefixBasedClassFilter(RunConfig.compactPackageNames(packages));
        }

        if (cmd.hasOption("outputPath")) {
            String root = cmd.getOptionValue("outputPath");
            outputPath =  root + File.separator + outputPath;
        }


        if (cmd.hasOption("threads")) {
            try {
                threadNum = Integer.valueOf(cmd.getOptionValue("threads"));
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid format for thread number. Set to default number : 1.", e);
            }
        }

        Parser parser = new Parser();
        // This variable must be a Map, not a Set.
        // Because there will be some .class files being included in multiple .jar files.
        // And each of them will be taken as a different ClassNode object.
        HashMap<String, ClassNode> targetClasses = new HashMap<>();
        for (String classFile : candidateClassFiles) {
            if (classFile.endsWith(".class")) {
                try {
                    targetClasses.putAll(parser.loadClasses(new File(classFile)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                // classFile is a .jar file, need to filter third-party class files
                // In fact, all third-party libraries are delivered as jar files
                try {
                    targetClasses.putAll(parser.loadClasses(new JarFile(classFile), classFilter));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        LOGGER.info("{} classes need to parse", targetClasses.size());
//        targetClasses.stream().forEach(s -> System.out.println(s.name));


        Set<String> methodEntries = new HashSet<>();
        for (Map.Entry<String, ClassNode> entry : targetClasses.entrySet()){
           String className = entry.getKey();
           ClassNode cn = entry.getValue();
            // Iterate methods in class
            for (MethodNode mn : cn.methods){
                // Iterate instructions in method
                for (AbstractInsnNode ain : mn.instructions.toArray()){
                    // If the instruction is loading a constant value
                    if (ain.getOpcode() == Opcodes.INVOKEVIRTUAL ||
                            ain.getOpcode() == Opcodes.INVOKEINTERFACE || ain.getOpcode() == Opcodes.INVOKESTATIC){
                        MethodInsnNode insnNode = (MethodInsnNode) ain;
                        String methodSig = insnNode.owner.replace("/", ".") + "." + insnNode.name;
                        if (loggingMethods.contains(methodSig)) {
                            methodEntries.add(getMethodSignature(className, mn.name, mn.desc));
                            break;
                        }
                    }
                }
            }
        }

        File output = new File(outputPath);
        try {
            FileUtils.writeLines(output, methodEntries);
        } catch (IOException e) {
            LOGGER.warn("Fail to write result to file {}. Output to console.", outputPath);
            methodEntries.forEach(s -> System.out.println(s));
        }

    }


    public static String getMethodSignature(String className, String methodName, String methodDescriptor) {
        String returnType = Type.getReturnType(methodDescriptor).getClassName();
        List<String> argsType = Arrays.stream(Type.getArgumentTypes(methodDescriptor))
                .map(Type::getClassName)
                .collect(Collectors.toList());
        return String.format("<%s: %s %s(%s)>", className, returnType, methodName, String.join(",", argsType));
    }


}
