package io.horizen.examples;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.horizen.account.AccountSidechainApp;
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

        int mcBlockReferenceDelay = 0;
        try {
            if (args.length >= 2) {
                mcBlockReferenceDelay = Integer.parseInt(args[1]);
            }
        } catch (NumberFormatException ex) {
            System.out.println("MC Block Reference delay can not be parsed.");
        }

        String settingsFileName = args[0];

        Injector injector = Guice.createInjector(new EvmAppModule(settingsFileName, mcBlockReferenceDelay));
        AccountSidechainApp sidechainApp = injector.getInstance(AccountSidechainApp.class);

        Logger logger = LogManager.getLogger(EvmApp.class);
        logger.info("...starting application...");

        sidechainApp.run();
        System.out.println("EVM Sidechain application successfully started...");
    }
}
