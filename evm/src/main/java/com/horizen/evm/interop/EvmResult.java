package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

import java.math.BigInteger;

public class EvmResult {
    public BigInteger usedGas;
    public String evmError;
    public byte[] returnData;
    public Address contractAddress;
    public Boolean reverted;
    public Object tracerResult;

    public static EvmResult emptyEvmResult() {
        var evmResult = new EvmResult();
        evmResult.usedGas = BigInteger.ZERO;
        evmResult.returnData = new byte[] {0};
        return evmResult;
    }

}
