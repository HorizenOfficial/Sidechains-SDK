package com.horizen.evm;

import com.horizen.evm.interop.EvmResult;

import java.math.BigInteger;

public final class Evm {
    private Evm() {
    }

    public static EvmResult Apply(StateDB stateDB, byte[] from, byte[] to, BigInteger value, byte[] input, BigInteger nonce, BigInteger gasLimit, BigInteger gasPrice)
        throws Exception {
        return LibEvm.evmApply(stateDB.handle, from, to, value, input, nonce, gasLimit, gasPrice);
    }
}
