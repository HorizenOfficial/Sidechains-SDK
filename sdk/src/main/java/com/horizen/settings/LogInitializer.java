package com.horizen.settings;

import com.horizen.SidechainSettings;
import org.apache.logging.log4j.Level;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

public class LogInitializer
{
    private static boolean initDone = false;
    private static Set<String> levelSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public static void initLogManager(SidechainSettings info)
    {
        if (initDone)
            return;
        initDone = true;

        // allowed levels
        levelSet.add("off");
        levelSet.add("fatal");
        levelSet.add("error");
        levelSet.add("warn");
        levelSet.add("info");
        levelSet.add("debug");
        levelSet.add("trace");
        levelSet.add("all");

        String logDir = info.scorexSettings().logDir().toString();
        String logFileName = info.logInfo().logFileName();
        String logFileNameWithPath = logDir + File.separator + logFileName;
        String logFileLevel = getCheckedLevel(info.logInfo().logFileLevel());
        String logConsoleLevel = getCheckedLevel(info.logInfo().logConsoleLevel());

        // init log4j2 logger
        System.setProperty("logFilename", logFileNameWithPath);
        System.setProperty("logFileLevel", logFileLevel);
        System.setProperty("logConsoleLevel", logConsoleLevel);

        org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(com.horizen.settings.LogInitializer.class);
        logger.log(Level.INFO,
                "Logging system started, log file: [" +logFileNameWithPath +
                "], file log level: [" + logFileLevel +
                "], console log level: [" + logConsoleLevel + "]");
    }

    public static String getCheckedLevel(String inLevel) {
        if (levelSet.contains(inLevel)) {
            return inLevel;
        }
        System.out.println("ERROR: specified log4j level: [" + inLevel + "] not valid: defaulting to [info]");
        return "info";
    }
}
