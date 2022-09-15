package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

import java.math.BigInteger;

public class EvmResult {
    public BigInteger usedGas;
    public String evmError;
    public byte[] returnData;
    public Address contractAddress;
    public EvmTraceLog[] traceLogs;

    public boolean isEmpty() {
        return usedGas == null && evmError == null && returnData == null && contractAddress == null && traceLogs == null;
    }
}
