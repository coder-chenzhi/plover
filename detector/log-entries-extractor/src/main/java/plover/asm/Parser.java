package plover.asm;

import plover.filter.ClassFilter;
import plover.filter.PrefixBasedClassFilter;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class Parser {

    private static final Logger LOGGER = LoggerFactory.getLogger(Parser.class);


    private PrefixBasedClassFilter specialFilter;


    public PrefixBasedClassFilter getSpecialFilter() {
        return specialFilter;
    }

    public void setSpecialFilter(PrefixBasedClassFilter specialFilter) {
        this.specialFilter = specialFilter;
    }

    Set<String> getEntries(Set<ClassNode> classNodes, Collection<String> methodSigs) {
        Set<String> entries = new HashSet<>();
        return entries;
    }

    HashMap<String, ClassNode> loadClasses(File classFile) throws IOException {
        HashMap<String, ClassNode> classes = new HashMap<>();
        InputStream is = new FileInputStream(classFile);
        return readClass(classFile.getName(), is, classes);
    }


    HashMap<String, ClassNode> loadClasses(JarFile jarFile, ClassFilter classFilter) throws IOException {
        HashMap<String, ClassNode> targetClasses = new HashMap<>();
        Stream<JarEntry> str = jarFile.stream();
        str.forEach(z -> readJar(jarFile, z, classFilter, targetClasses));
        jarFile.close();
        return targetClasses;
    }


    HashMap<String, ClassNode> readClass(String className, InputStream is, HashMap<String, ClassNode> targetClasses) {
        try {
            byte[] bytes = IOUtils.toByteArray(is);
            String cafebabe = String.format("%02X%02X%02X%02X", bytes[0], bytes[1], bytes[2], bytes[3]);
            if (!cafebabe.toLowerCase().equals("cafebabe")) {
                // This class doesn't have a valid magic
                return targetClasses;
            }
            ClassNode cn = getNode(bytes);
            targetClasses.putIfAbsent(className, cn);
        } catch (Exception e) {
            LOGGER.warn("Fail to read class {}", className, e);
        }
        return targetClasses;
    }



    HashMap<String, ClassNode> readJar(JarFile jar, JarEntry entry, ClassFilter classFilter, HashMap<String, ClassNode> targetClasses) {

        String name = entry.getName();
        if (name.endsWith(".class")) {
            String className = name.replace(".class", "").replace("/", ".");
            // if relevant options are not specified, classNames will be empty
            if (classFilter.shouldAnalyze(className)) {
                if (specialFilter == null || !specialFilter.shouldAnalyze(className)) {
                    try (InputStream jis = jar.getInputStream(entry)) {
                        return readClass(className, jis, targetClasses);
                    } catch (IOException e) {
                        LOGGER.warn("Fail to read class {} in jar {}", entry, jar.getName(), e);
                    }
                }
            }
        }
        return targetClasses;
    }


    ClassNode getNode(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        try {
            cr.accept(cn, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // garbage collection friendly
        cr = null;
        return cn;
    }

}
