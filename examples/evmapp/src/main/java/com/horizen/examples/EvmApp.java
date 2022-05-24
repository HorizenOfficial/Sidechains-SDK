package com.horizen.examples;

import com.google.inject.Guice;
import com.google.inject.Injector;

import com.horizen.account.AccountSidechainApp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;


public class EvmApp {
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

        Injector injector = Guice.createInjector(new EvmAppModule(settingsFileName));
        AccountSidechainApp sidechainApp = injector.getInstance(AccountSidechainApp.class);

        Logger logger = LogManager.getLogger(EvmApp.class);
        logger.info("...starting application...");
        
        sidechainApp.run();
        System.out.println("Simple Sidechain application successfully started...");
    }
}
