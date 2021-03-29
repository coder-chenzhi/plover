package plover.jdt;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Collection;

public class JDTMainEntry {

    public static void main(String[] args) throws Exception {
        String sourceDir = "E:\\zookeeper-3.4.13";
        File dir = new File(sourceDir);
        if (dir.isDirectory()) {
            Collection<File> allFiles = FileUtils.listFiles(dir, new String[]{"java"}, true);
            Visitor visitor = new Visitor();
            for (File file : allFiles) {
                String fileContent = FileUtils.readFileToString(file, "UTF-8");
                visitor.doVisit(file.getCanonicalPath().replace(sourceDir+File.separator, ""), fileContent);
            }
            System.out.println(visitor.getResult());
        }
    }

}
