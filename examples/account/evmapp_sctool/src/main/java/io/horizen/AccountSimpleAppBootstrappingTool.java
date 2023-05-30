package io.horizen;

import io.horizen.tools.utils.ConsolePrinter;

public class AccountSimpleAppBootstrappingTool extends AbstractScBootstrappingTool {
    public AccountSimpleAppBootstrappingTool() {
        super(new ConsolePrinter());
    }

    public static void main(String[] args) {
        AbstractScBootstrappingTool bootstrap = new AccountSimpleAppBootstrappingTool();
        bootstrap.startCommandTool(args);
    }

    @Override
    protected ScBootstrappingToolCommandProcessor getBootstrappingToolCommandProcessor() {
        return new ScBootstrappingToolCommandProcessor(printer, new AccountSimpleAppModel());
    }
}