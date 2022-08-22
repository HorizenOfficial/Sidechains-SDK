package com.horizen;

import com.horizen.tools.utils.ConsolePrinter;
import com.horizen.tools.utils.MessagePrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;

public class DbTool {
    public static final Set<String> storageNames = new HashSet<>(Arrays.asList(
            "secret",
            "wallet",
            "walletTransaction",
            "walletForgingStake",
            "walletCswDataStorage",
            "state",
            "stateForgerBox",
            "stateUtxoMerkleTree",
            "history",
            "consensusData"
    ));

    public static void main(String args[]) {

        // initialize log properties since this app uses log4j from sdk libraries
        // - default name for the log file
        String logFileName = System.getProperty("java.io.tmpdir") + File.separator + "db_tool.log";
        System.setProperty("logFilename", logFileName);
        // - default levels: all in the file and just errors on console
        System.setProperty("logFileLevel", "all");
        System.setProperty("logConsoleLevel", "error");

        Logger log = LogManager.getLogger(com.horizen.DbTool.class);

        // read database folder path from input arguments
        if (args.length == 0) {
            log.error("Please provide DB folder path as first parameter!");
            return;
        }
        if (!new File(args[0]).exists()) {
            log.error("Path " + args[0] + " doesn't exist");
            return;
        }
        String dataDirAbsolutePath = args[0];

        // read custom storage names list from input arguments
        List<String> customStorageNames = Arrays.asList(args[1].split(","));
        for(String customStorageName: customStorageNames) {
            storageNames.add(customStorageName);
        }

        MessagePrinter printer = new ConsolePrinter();
        DbToolCommandProcessor processor = new DbToolCommandProcessor(printer, dataDirAbsolutePath, log);
        if(args.length > 2)
            try {
                StringBuilder cmd = new StringBuilder(args[2]);
                for(int i=3; i<args.length; i++)
                    cmd.append(" ").append(args[i]);
                log.info("Starting db tool with cmd input: " + cmd);
                processor.processCommand(cmd.toString());
            } catch (Exception e) {
                printer.print(e.getMessage());
            }
        else{
            printer.print("Tool successfully started...\nPlease, enter the command:");
            Scanner scanner = new Scanner(System.in);
            while(true) {
                String input = scanner.nextLine();
                try {
                    if(input.startsWith("exit"))
                        break;
                    log.info("Starting db tool with cmd input: " + input);
                    processor.processCommand(input);
                }
                catch(Exception e) {
                    printer.print(e.getMessage());
                }
            }
        }
        log.info("... exiting db tool application.");
    }
}
