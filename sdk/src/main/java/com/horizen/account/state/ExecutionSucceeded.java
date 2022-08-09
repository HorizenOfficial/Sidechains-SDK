package com.horizen.account.state;

// Message was executed as expected.
public class ExecutionSucceeded implements ExecutionResult {
    private final byte[] returnData; // Returned data from invocation(function result or data supplied with revert opcode)

    public ExecutionSucceeded(byte[] returnData) {
        this.returnData = returnData;
    }

    @Override
    public boolean isFailed() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public boolean hasReturnData() {
        return returnData != null;
    }

    public byte[] returnData() {
        return returnData;
    }
}
