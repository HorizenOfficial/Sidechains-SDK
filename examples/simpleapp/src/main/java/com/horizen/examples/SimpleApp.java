package com.horizen.examples;

import com.horizen.SidechainApp;

import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.File;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.logging.*;

public class SimpleApp {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please provide settings file name as first parameter!");
            return;
        }

        if (!new File(args[0]).exists()) {
            System.out.println("File on path " + args[0] + " doesn't exist");
            return;
        }
        String settingsFileName = args[0];

        // default name for the log file
        String logFileName = "logs/debugLog.txt";
        if (args.length == 2) {
            logFileName = args[1];
        }

        // filter noisy external libraries traces that are redirected to console, just keep warnings
        java.util.logging.Logger rootLogger = java.util.logging.LogManager.getLogManager().getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        for (Handler h : rootLogger.getHandlers()) {
            if (h instanceof java.util.logging.ConsoleHandler) {
                h.setLevel(Level.WARNING);
            }
        }

        // init log4j2 logger
        System.setProperty("logFilename", logFileName);
        Logger logger = LogManager.getLogger(com.horizen.examples.SimpleApp.class);

        Injector injector = Guice.createInjector(new SimpleAppModule(settingsFileName));
        SidechainApp sidechainApp = injector.getInstance(SidechainApp.class);

        logger.info("...starting application...");
        sidechainApp.run();

        System.out.println("Simple Sidechain application successfully started, using log file: [" + logFileName + "]...");
    }
}
