package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

import java.math.BigInteger;

public class EvmResult {
    public BigInteger usedGas;
    public String evmError;
    public byte[] returnData;
    public Address contractAddress;
    public EvmTraceLog[] traceLogs;
    public Boolean reverted;

    public static EvmResult emptyEvmResult() {
        var evmResult = new EvmResult();

        evmResult.usedGas = BigInteger.ZERO;
        evmResult.returnData = new byte[] {0};
        evmResult.traceLogs = new EvmTraceLog[] {};

        return evmResult;
    }
}
