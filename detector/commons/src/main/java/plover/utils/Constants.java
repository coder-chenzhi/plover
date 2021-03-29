package plover.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

public class Constants {

    // TODO change to options, and support asterisk wildcard
    public static final List<String> DEFAULT_LOGGING_METHOD = Arrays.asList(
            // slf4j
            "org.slf4j.Logger.trace",
            "org.slf4j.Logger.debug",
            "org.slf4j.Logger.info",
            "org.slf4j.Logger.warn",
            "org.slf4j.Logger.error",
            //commons-logging,
            "org.apache.commons.logging.Log.trace",
            "org.apache.commons.logging.Log.debug",
            "org.apache.commons.logging.Log.info",
            "org.apache.commons.logging.Log.warn",
            "org.apache.commons.logging.Log.error",
            "org.apache.commons.logging.Log.fatal",
            // Log4j 2.x
            "org.apache.logging.log4j.Logger.log",
            "org.apache.logging.log4j.Logger.trace",
            "org.apache.logging.log4j.Logger.debug",
            "org.apache.logging.log4j.Logger.info",
            "org.apache.logging.log4j.Logger.warn",
            "org.apache.logging.log4j.Logger.error",
            "org.apache.logging.log4j.Logger.fatal",
            // Log4j 1.x
            "org.apache.log4j.Logger.trace",
            "org.apache.log4j.Category.debug",
            "org.apache.log4j.Category.info",
            "org.apache.log4j.Category.warn",
            "org.apache.log4j.Category.error",
            "org.apache.log4j.Category.fatal",
            "org.apache.log4j.Category.log",
            //JUL
            "java.util.logging.Logger.logp",
            "java.util.logging.Logger.logrb",
            "java.util.logging.Logger.finest",
            "java.util.logging.Logger.finer",
            "java.util.logging.Logger.fine",
            "java.util.logging.Logger.config",
            "java.util.logging.Logger.info",
            "java.util.logging.Logger.warning",
            "java.util.logging.Logger.sever"
    );

    // TODO change to options, and support asterisk wildcard
    public static final List<String> DEFAULT_DEBUG_LOGGING_METHOD = Arrays.asList(
            // slf4j
            "org.slf4j.Logger.trace",
            "org.slf4j.Logger.debug",
            //commons-logging,
            "org.apache.commons.logging.Log.trace",
            "org.apache.commons.logging.Log.debug",
            // Log4j 2.x
            "org.apache.logging.log4j.Logger.trace",
            "org.apache.logging.log4j.Logger.debug",
            // Log4j 1.x
            "org.apache.log4j.Logger.trace",
            "org.apache.log4j.Category.debug",
            //JUL
            "java.util.logging.Logger.finest",
            "java.util.logging.Logger.finer",
            "java.util.logging.Logger.fine",
            "java.util.logging.Logger.config"
    );

    public static final List<String> DEFAULT_GUARD_METHOD_SIGNATURE = Arrays.asList(
            // slf4J
            "<org.slf4j.Logger: boolean isErrorEnabled()>",
            "<org.slf4j.Logger: boolean isWarnEnabled()>",
            "<org.slf4j.Logger: boolean isInfoEnabled()>",
            "<org.slf4j.Logger: boolean isDebugEnabled()>",
            "<org.slf4j.Logger: boolean isTraceEnabled()>",
            "<org.slf4j.Logger: boolean isErrorEnabled(org.slf4j.Marker)>",
            "<org.slf4j.Logger: boolean isWarnEnabled(org.slf4j.Marker)>",
            "<org.slf4j.Logger: boolean isInfoEnabled(org.slf4j.Marker)>",
            "<org.slf4j.Logger: boolean isDebugEnabled(org.slf4j.Marker)>",
            "<org.slf4j.Logger: boolean isTraceEnabled(org.slf4j.Marker)>",
            // commons-logging
            "<org.apache.commons.logging.Log: boolean isFatalEnabled()>",
            "<org.apache.commons.logging.Log: boolean isErrorEnabled()>",
            "<org.apache.commons.logging.Log: boolean isWarnEnabled()>",
            "<org.apache.commons.logging.Log: boolean isInfoEnabled()>",
            "<org.apache.commons.logging.Log: boolean isDebugEnabled()>",
            "<org.apache.commons.logging.Log: boolean isTraceEnabled()>",
            // Log4j 1.x
            "<org.apache.log4j.Category: boolean isInfoEnabled()>",
            "<org.apache.log4j.Category: boolean isDebugEnabled()>",
            "<org.apache.log4j.Logger: boolean isTraceEnabled()>",
            // Log4j 2.x
            "<org.apache.logging.log4j.Logger: boolean isFatalEnabled()>",
            "<org.apache.logging.log4j.Logger: boolean isErrorEnabled()>",
            "<org.apache.logging.log4j.Logger: boolean isWarnEnabled()>",
            "<org.apache.logging.log4j.Logger: boolean isInfoEnabled()>",
            "<org.apache.logging.log4j.Logger: boolean isDebugEnabled()>",
            "<org.apache.logging.log4j.Logger: boolean isTraceEnabled()>",
            "<org.apache.logging.log4j.Logger: boolean isFatalEnabled(org.apache.logging.log4j.Marker)>",
            "<org.apache.logging.log4j.Logger: boolean isErrorEnabled(org.apache.logging.log4j.Marker)>",
            "<org.apache.logging.log4j.Logger: boolean isWarnEnabled(org.apache.logging.log4j.Marker)>",
            "<org.apache.logging.log4j.Logger: boolean isInfoEnabled(org.apache.logging.log4j.Marker)>",
            "<org.apache.logging.log4j.Logger: boolean isDebugEnabled(org.apache.logging.log4j.Marker)>",
            "<org.apache.logging.log4j.Logger: boolean isTraceEnabled(org.apache.logging.log4j.Marker)>",
            // JUL
            "<java.util.logging.Logger: boolean isLoggable(java.util.logging.Level)>"
    );

    public static final List<String> DEFAULT_DEBUG_GUARD_METHOD_SIGNATURE = Arrays.asList(
            // slf4j
            "<org.slf4j.Logger: boolean isDebugEnabled()>",
            "<org.slf4j.Logger: boolean isTraceEnabled()>",
            "<org.slf4j.Logger: boolean isDebugEnabled(org.slf4j.Marker)>",
            "<org.slf4j.Logger: boolean isTraceEnabled(org.slf4j.Marker)>",
            // commons-logging
            "<org.apache.commons.logging.Log: boolean isDebugEnabled()>",
            "<org.apache.commons.logging.Log: boolean isTraceEnabled()>",
            // Log4j 1.x
            "<org.apache.log4j.Category: boolean isDebugEnabled()>",
            "<org.apache.log4j.Logger: boolean isTraceEnabled()>",
            // Log4j 2.x
            "<org.apache.logging.log4j.Logger: boolean isDebugEnabled()>",
            "<org.apache.logging.log4j.Logger: boolean isTraceEnabled()>",
            "<org.apache.logging.log4j.Logger: boolean isDebugEnabled(org.apache.logging.log4j.Marker)>",
            "<org.apache.logging.log4j.Logger: boolean isTraceEnabled(org.apache.logging.log4j.Marker)>",
            // JUL
            "<java.util.logging.Logger: boolean isLoggable(java.util.logging.Level)>"
    );

    // TODO maybe too conservative
    public static final List<String> DEFAULT_IO_METHOD = Arrays.asList(
            // JDK
            "java.io",
            "java.nio",
            "java.net",
            "java.rmi",
            "javax.net",
            "javax.imageio",
            "javax.management",
            "javax.rmi",
            "javax.sql"
    );

    public static final List<String> CUSTOMIZED_IO_METHOD = new ArrayList<>();

    static {
        try {
            Reader reader = new InputStreamReader(ResourceUtils.getResourceStream("IOMethods.txt"));
            BufferedReader bufReader = new BufferedReader(reader);
            String line = bufReader.readLine();
            while (line != null) {
                if (!line.isEmpty() && !line.startsWith("%"))
                    CUSTOMIZED_IO_METHOD.add(line);
                line = bufReader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
