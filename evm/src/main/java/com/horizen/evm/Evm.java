package com.horizen.evm;

import com.horizen.evm.library.EvmApplyResult;
import com.horizen.evm.library.EvmResult;
import com.horizen.evm.library.LibEvm;

import java.math.BigInteger;

public final class Evm {
    private Evm() {
    }

    public static EvmResult Execute(StateDB stateDB, byte[] from, byte[] to, BigInteger value, byte[] input)
        throws Exception {
        return LibEvm.evmExecute(stateDB.handle, from, to, value, input);
    }

    public static EvmApplyResult Apply(StateDB stateDB, byte[] from, byte[] to, BigInteger value, byte[] input)
        throws Exception {
        return LibEvm.evmApply(stateDB.handle, from, to, value, input);
    }
}
