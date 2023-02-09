package com.horizen.evm;

import com.horizen.evm.interop.EvmParams;
import com.horizen.evm.interop.EvmResult;
import com.horizen.evm.interop.TraceOptions;
import com.horizen.evm.utils.Address;

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
        TraceOptions traceOptions,
        BlockHashCallback blockHashGetter
    ) {
        Integer blockHashCallbackHandle = null;
        try {
            if (blockHashGetter != null) {
                blockHashCallbackHandle = LibEvm.registerCallback(blockHashGetter);
            }
            var params = new EvmParams(
                stateDBHandle.handle, from, to, value, input, gasLimit, gasPrice, context, traceOptions,
                blockHashCallbackHandle
            );
            return LibEvm.invoke("EvmApply", params, EvmResult.class);
        } finally {
            if (blockHashCallbackHandle != null) {
                LibEvm.unregisterCallback(blockHashCallbackHandle);
            }
        }
    }
}
