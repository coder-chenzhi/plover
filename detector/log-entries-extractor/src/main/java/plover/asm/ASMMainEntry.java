package plover.asm;

import plover.utils.Constants;
import plover.filter.ClassFilter;
import plover.utils.RunConfig;
import plover.filter.PrefixBasedClassFilter;
import org.apache.commons.io.FileUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ASMMainEntry {

    private final static Logger LOGGER = LoggerFactory.getLogger(ASMMainEntry.class);

    public static void main(String[] args) throws Exception {
        RunConfig runConfig = new RunConfig(false);
        String projectName = "hive";
        String classpath = runConfig.getSootClassPath(projectName);
        ClassFilter packageFilter = new PrefixBasedClassFilter(
                RunConfig.compactPackageNames(runConfig.getAllPackagesFromSource(projectName)));
        List<String> loggingMethods = runConfig.getLoggingMethods(projectName);
        loggingMethods.addAll(Constants.DEFAULT_DEBUG_LOGGING_METHOD);

        Set<String> candidateClassFiles = new HashSet<>();
        for (String entry : classpath.split(File.pathSeparator)) {
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

        Parser parser = new Parser();
        if (projectName == "hbase") {
            parser.setSpecialFilter(new PrefixBasedClassFilter(Arrays.asList("org.apache.hadoop.hbase.shaded",
                    "org.apache.hadoop.metrics2.lib.MutableMetricsFactory")));
        } else if (projectName == "hive") {
            parser.setSpecialFilter(new PrefixBasedClassFilter(Arrays.asList("org.apache.hive.druid.io",
                    "org.apache.hive.druid.com", "org.apache.hive.druid.org", "org.apache.hadoop.mapreduce",
                    "org.apache.hadoop.mapred", "org.apache.hadoop.fs", "org.apache.hadoop.security")));
        }
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
                    targetClasses.putAll(parser.loadClasses(new JarFile(classFile), packageFilter));
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
        System.out.println("Logging entries for " + projectName + ":");
        System.out.println("=========================================");
        methodEntries.forEach(s -> System.out.println(s));

    }

    public static String getMethodSignature(String className, String methodName, String methodDescriptor) {
        String returnType = Type.getReturnType(methodDescriptor).getClassName();
        List<String> argsType = Arrays.stream(Type.getArgumentTypes(methodDescriptor))
                .map(Type::getClassName)
                .collect(Collectors.toList());
        return String.format("<%s: %s %s(%s)>", className, returnType, methodName, String.join(",", argsType));
    }
}
