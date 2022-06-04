package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

public class EvmApplyResult {
    public long usedGas;
    public String evmError;
    public byte[] returnData;
    public Address contractAddress;
    public Log[] logs;

    public static class Log {
    }
}
