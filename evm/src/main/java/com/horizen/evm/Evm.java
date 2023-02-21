package com.horizen.evm;

import com.horizen.evm.params.EvmParams;
import com.horizen.evm.results.EvmResult;

import java.math.BigInteger;

public final class Evm {
    private Evm() { }

    public static EvmResult Apply(
        ResourceHandle stateDBHandle,
        Address from,
        Address to,
        BigInteger value,
        byte[] input,
        BigInteger gasLimit,
        BigInteger gasPrice,
        EvmContext context,
        TraceOptions traceOptions
    ) {
        var params = new EvmParams(
            stateDBHandle.handle, from, to, value, input, gasLimit, gasPrice, context, traceOptions);
        return LibEvm.invoke("EvmApply", params, EvmResult.class);
    }
}
