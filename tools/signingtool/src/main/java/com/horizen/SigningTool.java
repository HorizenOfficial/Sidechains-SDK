package com.horizen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horizen.tools.utils.ConsolePrinter;
import com.horizen.tools.utils.MessagePrinter;

public class SigningTool {

    public static void main(String[] args) {
        MessagePrinter printer = new ConsolePrinter();
        SigningToolCommandProcessor processor = new SigningToolCommandProcessor(printer);

        try {
            if (args.length > 0) {
                StringBuilder cmd = new StringBuilder(args[0]);
                for (int i = 1; i < args.length; i++)
                    cmd.append(" ").append(args[i]);
                processor.processCommand(cmd.toString());
            } else {
                processor.processCommand("help");
            }
        } catch (Exception e) {
            ObjectNode resJson = new ObjectMapper().createObjectNode();
            resJson.put("error", e.getMessage());

            printer.print(resJson.toString());
        }
    }
}
