package com.horizen.account.state;

public class EvmException extends Exception {
    public final byte[] returnData;

    public EvmException(String evmError, byte[] returnData) {
        super(evmError);
        this.returnData = returnData;
    }
}
