package io.horizen;

import io.horizen.tools.utils.ConsolePrinter;

public class EVMSimpleAppBootstrappingTool extends AbstractScBootstrappingTool {
    public EVMSimpleAppBootstrappingTool() {
        super(new ConsolePrinter());
    }

    public static void main(String[] args) {
        AbstractScBootstrappingTool bootstrap = new EVMSimpleAppBootstrappingTool();
        bootstrap.startCommandTool(args);
    }

    @Override
    protected ScBootstrappingToolCommandProcessor getBootstrappingToolCommandProcessor() {
        return new ScBootstrappingToolCommandProcessor(printer, new EVMSimpleAppModel());
    }
}