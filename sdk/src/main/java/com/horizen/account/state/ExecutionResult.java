package com.horizen.account.state;

import java.math.BigInteger;

public interface ExecutionResult {
    boolean isFailed();

    boolean isValid();

    BigInteger gasUsed();
}
