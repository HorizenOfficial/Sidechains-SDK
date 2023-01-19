package com.horizen.evm;

import com.horizen.evm.interop.EvmContext;
import com.horizen.evm.interop.EvmParams;
import com.horizen.evm.interop.EvmResult;
import com.horizen.evm.interop.TraceOptions;

import java.math.BigInteger;

public final class Evm {
    private Evm() {}

    public static EvmResult Apply(
        ResourceHandle stateDBHandle,
        byte[] from,
        byte[] to,
        BigInteger value,
        byte[] input,
        BigInteger gasLimit,
        BigInteger gasPrice,
        EvmContext context,
        TraceOptions traceOptions
    ) {
        var params = new EvmParams(stateDBHandle.handle, from, to, value, input, gasLimit, gasPrice, context, traceOptions);
        return LibEvm.invoke("EvmApply", params, EvmResult.class);
    }
}
