package io.horizen.tools.utils;

import java.io.IOException;


public abstract class CommandProcessor {
    protected MessagePrinter printer;
    public CommandProcessor(MessagePrinter printer) {
        this.printer = printer;
    }

    protected abstract void processCommand(String input) throws Exception;

    protected abstract Command parseCommand(String input) throws IOException;

    protected abstract void printUsageMsg();

    protected void printUnsupportedCommandMsg(String command) {
        printer.print(String.format("Error: unsupported command '%s'.\nSee 'help' for usage guideline.", command));
    }

}
