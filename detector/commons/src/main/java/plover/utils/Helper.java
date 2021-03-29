package plover.utils;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.util.Date;

public class Helper {

    /** Calculate the time between two events */
    public static String getTimeConsumed(Date start, Date end){
        long time = end.getTime()-start.getTime();
        return ""+time/1000+"."+(time/100)%10+"s";
    }

    public static void getMethodSignatures(String className) throws Exception {
        boolean isLocal = true;
        RunConfig runConfig = new RunConfig(isLocal);
        String classpath = runConfig.projectsRoot.get("{JAVA_HOME}");
        SootExecutorUtil.setDefaultSootOptions(classpath);
        Scene.v().loadClass(className, SootClass.BODIES);
        SootClass clazz = Scene.v().getSootClass(className);
        for (SootMethod method : clazz.getMethods()) {
            System.out.println(method.getSignature());
        }
    }

    public static void main(String[] args) throws Exception {
        String className = "java.sql.PreparedStatement";
        getMethodSignatures(className);
    }

}
