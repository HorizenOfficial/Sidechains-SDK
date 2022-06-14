package com.horizen.account.state;

import java.math.BigInteger;

// Message itself is invalid or doesn't satisfy basic checks of a specific MessageProcessor.
// For example, insufficient balance of the spender.
public class InvalidMessage implements ExecutionResult {
    private final Exception reason;

    public InvalidMessage(Exception reason) {
        this.reason = reason;
    }

    @Override
    public boolean isFailed() {
        return true;
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public BigInteger gasUsed() {
        return BigInteger.ZERO;
    }

    public Exception getReason() {
        return reason;
    }
}
