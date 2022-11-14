package com.horizen.settings;

import com.horizen.SidechainSettings;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class LogInitializer {
    private static boolean initDone = false;
    // note: need to use a set that preserves the insertion-order
    private static final Set<String> levelSet = new LinkedHashSet<>();

    public static void initLogManager(SidechainSettings info) {
        if (initDone)
            return;
        initDone = true;

        // allowed levels
        // note: the order is important here, see below
        levelSet.add("all");
        levelSet.add("trace");
        levelSet.add("debug");
        levelSet.add("info");
        levelSet.add("warn");
        levelSet.add("error");
        levelSet.add("fatal");
        levelSet.add("off");

        String logDir = info.sparkzSettings().logDir().toString();
        if (!logDir.endsWith(File.separator)) {
            logDir = logDir + File.separator;
        }
        String logFileName = info.logInfo().logFileName();
        String logFileLevel = getCheckedLevel(info.logInfo().logFileLevel());
        String logConsoleLevel = getCheckedLevel(info.logInfo().logConsoleLevel());
        String logRootLevel = "all";
        // reduce root log level to the highest level applied to either file or console appender
        for (var level : levelSet) {
            if (level.equals(logFileLevel) || level.equals(logConsoleLevel)) {
                logRootLevel = level;
                break;
            }
        }

        // init log4j2 logger
        System.setProperty("logDir", logDir);
        System.setProperty("logFilename", logFileName);
        System.setProperty("logRootLevel", logRootLevel);
        System.setProperty("logFileLevel", logFileLevel);
        System.setProperty("logConsoleLevel", logConsoleLevel);

        Logger logger = LogManager.getLogger(LogInitializer.class);
        logger.log(
            Level.INFO,
            "Logging system started, log file: [{}], file log level: [{}], console log level: [{}]",
            logDir + File.separator + logFileName, logFileLevel, logConsoleLevel
        );
    }

    public static String getCheckedLevel(String inLevel) {
        if (levelSet.contains(inLevel)) {
            return inLevel;
        }
        System.out.println("ERROR: specified log4j level: [" + inLevel + "] not valid: defaulting to [info]");
        return "info";
    }
}
