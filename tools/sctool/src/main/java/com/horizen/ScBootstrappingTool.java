package com.horizen;
import java.io.File;
import java.util.Scanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ScBootstrappingTool {
    public static void main(String[] args) {

        // initialize log properties since this app uses log4j from sdk libraries
        // - no log dir here
        System.setProperty("logDir", "");
        // - default name for the log file
        String logFileName = System.getProperty("java.io.tmpdir") + File.separator + "sc_bootstrapping_tool.log";
        System.setProperty("logFilename", logFileName);
        // - default levels: all in the file and just errors on console
        System.setProperty("logFileLevel", "all");
        System.setProperty("logConsoleLevel", "error");

        Logger logger = LogManager.getLogger(com.horizen.ScBootstrappingTool.class);

        MessagePrinter printer = new ConsolePrinter();
        CommandProcessor processor = new CommandProcessor(printer);
        if(args.length > 0)
            try {
                StringBuilder cmd = new StringBuilder(args[0]);
                for(int i=1; i<args.length; i++)
                    cmd.append(" ").append(args[i]);
                logger.info("Starting bootstrapping tool with cmd input: " + cmd);
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
                    logger.info("Starting bootstrapping tool with cmd input: " + input);
                    processor.processCommand(input);
                }
                catch(Exception e) {
                    printer.print(e.getMessage());
                }
            }
        }
        logger.info("... exiting bootstrapping tool application.");

    }
}
