package com.horizen.account.state;

import java.math.BigInteger;

public interface ExecutionResult {
    boolean isFailed();

    boolean isValid();

    default BigInteger gasUsed() {
        return BigInteger.ZERO;
    }
}
