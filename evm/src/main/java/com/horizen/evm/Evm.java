package com.horizen.evm;

import com.horizen.evm.interop.EvmContext;
import com.horizen.evm.interop.EvmResult;

import java.math.BigInteger;

public final class Evm {
    private Evm() {
    }

    public static EvmResult Apply(
        StateDB stateDB,
        byte[] from,
        byte[] to,
        BigInteger value,
        byte[] input,
        BigInteger nonce,
        BigInteger gasLimit,
        BigInteger gasPrice,
        EvmContext context
    ) {
        return LibEvm.evmApply(stateDB.handle, from, to, value, input, nonce, gasLimit, gasPrice, context);
    }

    public static EvmResult Apply(
        StateDB stateDB,
        byte[] from,
        byte[] to,
        BigInteger value,
        byte[] input,
        BigInteger nonce,
        BigInteger gasLimit,
        BigInteger gasPrice
    ) {
        return LibEvm.evmApply(stateDB.handle, from, to, value, input, nonce, gasLimit, gasPrice, null);
    }
}
