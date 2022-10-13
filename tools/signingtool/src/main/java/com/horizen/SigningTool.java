package com.horizen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horizen.tools.utils.ConsolePrinter;
import com.horizen.tools.utils.MessagePrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;

public class SigningTool {

    public static void main(String[] args) {

        // initialize log properties since this app uses log4j from sdk libraries
        // - no log dir here
        System.setProperty("logDir", "");
        // - default name for the log file
        String logFileName = System.getProperty("java.io.tmpdir") + File.separator + "signing_tool.log";
        System.setProperty("logFilename", logFileName);
        // - default levels: all in the file and just errors on console
        System.setProperty("logFileLevel", "all");
        System.setProperty("logConsoleLevel", "error");

        Logger logger = LogManager.getLogger(com.horizen.SigningTool.class);

        MessagePrinter printer = new ConsolePrinter();
        SigningToolCommandProcessor processor = new SigningToolCommandProcessor(printer);
        try {
            if (args.length > 0) {
                StringBuilder cmd = new StringBuilder(args[0]);
                for (int i = 1; i < args.length; i++)
                    cmd.append(" ").append(args[i]);
                logger.info("Starting signingtool tool with cmd input: " + cmd);
                processor.processCommand(cmd.toString());
            } else {
                processor.processCommand("help");
            }
        } catch (Exception e) {
            ObjectNode resJson = new ObjectMapper().createObjectNode();
            resJson.put("error", e.getMessage());

            printer.print(resJson.toString());
        }

        logger.info("... exiting signingtool tool application.");

    }
}
