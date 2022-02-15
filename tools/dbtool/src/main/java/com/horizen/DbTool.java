package com.horizen;
import java.io.File;
import java.util.*;

import com.horizen.settings.SettingsReader;
import com.horizen.storage.Storage;
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

        if (args.length == 0) {
            log.error("Please provide settings file name as first parameter!");
            return;
        }
        if (!new File(args[0]).exists()) {
            log.error("File on path " + args[0] + " doesn't exist");
            return;
        }
        String settingsFileName = args[0];

        SettingsReader settingsReader= new SettingsReader(settingsFileName, Optional.empty());
        SidechainSettings sidechainSettings = settingsReader.getSidechainSettings();

        String dataDirAbsolutePath = sidechainSettings.scorexSettings().dataDir().getAbsolutePath();

        File secretStore = new File(dataDirAbsolutePath + "/secret");
        File walletBoxStore = new File(dataDirAbsolutePath + "/wallet");
        File walletTransactionStore = new File(dataDirAbsolutePath + "/walletTransaction");
        File walletForgingBoxesInfoStorage = new File(dataDirAbsolutePath + "/walletForgingStake");
        File walletCswDataStorage = new File(dataDirAbsolutePath + "/walletCswDataStorage");
        File stateForgerBoxStore = new File(dataDirAbsolutePath + "/stateForgerBox");
        File stateUtxoMerkleTreeStore = new File(dataDirAbsolutePath + "/stateUtxoMerkleTree");
        File historyStore = new File(dataDirAbsolutePath + "/history");
        File consensusStore = new File(dataDirAbsolutePath + "/consensusData");



        MessagePrinter printer = new ConsolePrinter();
        CommandProcessor processor = new CommandProcessor(printer, dataDirAbsolutePath, log);
        if(args.length > 1)
            try {
                StringBuilder cmd = new StringBuilder(args[1]);
                for(int i=2; i<args.length; i++)
                    cmd.append(" ").append(args[i]);
                log.info("Starting db tool with cmd input: " + cmd);
                processor.processCommand(cmd.toString());
            }catch (Exception e){
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
