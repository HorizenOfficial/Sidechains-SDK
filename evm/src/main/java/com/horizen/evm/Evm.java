package com.horizen.evm;

import com.horizen.evm.interop.EvmContext;
import com.horizen.evm.interop.EvmResult;
import com.horizen.evm.interop.TraceParams;

import java.math.BigInteger;

public final class Evm {
    private Evm() {
    }

    public static EvmResult Apply(
            ResourceHandle stateDBHandle,
            byte[] from,
            byte[] to,
            BigInteger value,
            byte[] input,
            BigInteger gasLimit,
            BigInteger gasPrice,
            EvmContext context,
            TraceParams traceParams
    ) {
        return LibEvm.evmApply(stateDBHandle.handle, from, to, value, input, gasLimit, gasPrice, context, traceParams);
    }
}
