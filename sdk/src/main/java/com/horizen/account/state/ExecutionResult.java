package com.horizen.account.state;

import java.math.BigInteger;

interface ExecutionResult {
    boolean isFailed();

    boolean isValid();

    BigInteger gasUsed();
}
