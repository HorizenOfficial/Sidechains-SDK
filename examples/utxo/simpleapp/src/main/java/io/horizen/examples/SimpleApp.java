package io.horizen.examples;

import io.horizen.history.AbstractHistory;
import io.horizen.utxo.SidechainApp;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;


public class SimpleApp {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Please provide settings file name as first parameter!");
            return;
        }

        if (!new File(args[0]).exists()) {
            System.out.println("File on path " + args[0] + " doesn't exist");
            return;
        }
        String settingsFileName = args[0];

        int mcBlockReferenceDelay = 0;
        try {
            if (args.length >= 2) {
                mcBlockReferenceDelay = Integer.parseInt(args[1]);
            }
        } catch (NumberFormatException ex) {
            System.out.println("MC Block Referenced delay can not be parsed.");
        }

        boolean allForksEnabled = false;
        if (args.length >= 3) {
            allForksEnabled = Boolean.parseBoolean(args[2]);
        }

        int maxHistRewLen = AbstractHistory.MAX_HISTORY_REWRITING_LENGTH();
        try {
            if (args.length >= 4) {
                maxHistRewLen = Integer.parseInt(args[3]);
            }
        } catch (NumberFormatException ex) {
            System.out.println("Max History rewrite Length can not be parsed.");
        }


        Injector injector = Guice.createInjector(new SimpleAppModule(settingsFileName, mcBlockReferenceDelay, allForksEnabled, maxHistRewLen));
        SidechainApp sidechainApp = injector.getInstance(SidechainApp.class);

        Logger logger = LogManager.getLogger(io.horizen.examples.SimpleApp.class);
        logger.info("...starting application...");
        
        sidechainApp.run();
        System.out.println("Simple Sidechain application successfully started...");
    }
}
