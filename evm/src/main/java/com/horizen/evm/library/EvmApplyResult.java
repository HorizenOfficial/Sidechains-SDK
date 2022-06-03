package com.horizen.evm.library;

public class EvmApplyResult {
    public long usedGas;
    public String evmError;
    public byte[] returnData;
    public String contractAddress;
    public Log[] logs;

    public static class Log {
    }
}
