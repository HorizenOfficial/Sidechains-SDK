package io.horizen;

import io.horizen.tools.utils.ConsolePrinter;

public class UTXOSimpleAppBootstrappingTool extends AbstractScBootstrappingTool {
    public UTXOSimpleAppBootstrappingTool() {
        super(new ConsolePrinter());
    }

    public static void main(String[] args) {
        AbstractScBootstrappingTool bootstrap = new UTXOSimpleAppBootstrappingTool();
        bootstrap.startCommandTool(args);
    }

    @Override
    protected ScBootstrappingToolCommandProcessor getBootstrappingToolCommandProcessor() {
        return new ScBootstrappingToolCommandProcessor(printer, new UTXOSimpleAppModel());
    }
}