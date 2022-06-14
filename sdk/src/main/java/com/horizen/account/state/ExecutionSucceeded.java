package com.horizen.account.state;

import java.math.BigInteger;


// Message was executed as expected.
public class ExecutionSucceeded implements ExecutionResult {
    private final BigInteger gasUsed; // Total used gas during Message processing
    private final byte[] returnData; // Returned data from invocation(function result or data supplied with revert opcode)

    public ExecutionSucceeded(BigInteger gasUsed, byte[] returnData) {
        this.gasUsed = gasUsed;
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

    @Override
    public BigInteger gasUsed() {
        return gasUsed;
    }

    public boolean hasReturnData() {
        return returnData != null;
    }

    public byte[] returnData() {
        return returnData;
    }
}
