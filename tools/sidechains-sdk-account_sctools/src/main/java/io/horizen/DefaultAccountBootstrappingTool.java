package io.horizen;

import io.horizen.tools.utils.ConsolePrinter;

public class DefaultAccountBootstrappingTool extends AbstractScBootstrappingTool {
    public DefaultAccountBootstrappingTool() {
        super(new ConsolePrinter());
    }

    public static void main(String[] args) {
        AbstractScBootstrappingTool bootstrap = new DefaultAccountBootstrappingTool();
        bootstrap.startCommandTool(args);
    }

    @Override
    protected ScBootstrappingToolCommandProcessor getBootstrappingToolCommandProcessor() {
        return new ScBootstrappingToolCommandProcessor(printer, new AccountModel());
    }
}