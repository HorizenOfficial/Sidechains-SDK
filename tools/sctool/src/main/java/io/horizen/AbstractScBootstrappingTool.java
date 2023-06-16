package io.horizen;

import io.horizen.settings.LogInitializer;
import io.horizen.tools.utils.MessagePrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Scanner;

public abstract class AbstractScBootstrappingTool {
    protected final MessagePrinter printer;
    public AbstractScBootstrappingTool(MessagePrinter printer) {
        this.printer = printer;
    }

    protected abstract ScBootstrappingToolCommandProcessor getBootstrappingToolCommandProcessor();

    public void startCommandTool(String[] args) {
        // initialize log properties since this app uses log4j from sdk libraries
        // - temporary log dir
        String logDir = System.getProperty("java.io.tmpdir");
        // - default name for the log file
        String logFileName = "sc_bootstrapping_tool.log";
        // - default levels: all in the file and just errors on console
        LogInitializer.initLogManager(logDir, logFileName, "all", "error");
        Logger logger = LogManager.getLogger(AbstractScBootstrappingTool.class);

        ScBootstrappingToolCommandProcessor processor = getBootstrappingToolCommandProcessor();
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
