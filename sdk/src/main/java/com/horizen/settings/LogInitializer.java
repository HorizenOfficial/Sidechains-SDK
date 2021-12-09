package com.horizen.settings;

import com.horizen.SidechainSettings;
import org.apache.logging.log4j.Level;

import java.io.File;

public class LogInitializer
{
    private static boolean initDone = false;

    public static void initLogManager(SidechainSettings info)
    {
        if (initDone)
            return;

        initDone = true;

        String logDir = info.scorexSettings().logDir().toString();
        String logFileName = info.logInfo().logFileName();
        String logFileNameWithPath = logDir + File.separator + logFileName;
        String logFileLevel = info.logInfo().logFileLevel();
        String logConsoleLevel = info.logInfo().logConsoleLevel();

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
}
