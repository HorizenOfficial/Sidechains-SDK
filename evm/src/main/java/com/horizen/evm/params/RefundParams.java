package com.horizen.evm.params;

import java.math.BigInteger;

public class RefundParams extends HandleParams {
    public final BigInteger gas;

    public RefundParams(int handle, BigInteger gas) {
        super(handle);
        this.gas = gas;
    }
}
