package io.horizen;

import io.horizen.tools.utils.ConsolePrinter;

public class DefaultUTXOBootstrappingTool extends AbstractScBootstrappingTool {
    public DefaultUTXOBootstrappingTool() {
        super(new ConsolePrinter());
    }

    public static void main(String[] args) {
        AbstractScBootstrappingTool bootstrap = new DefaultUTXOBootstrappingTool();
        bootstrap.startCommandTool(args);
    }

    @Override
    protected ScBootstrappingToolCommandProcessor getBootstrappingToolCommandProcessor() {
        return new ScBootstrappingToolCommandProcessor(printer, new UTXOModel());
    }
}