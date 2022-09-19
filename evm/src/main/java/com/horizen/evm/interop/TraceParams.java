package com.horizen.evm.interop;

public class TraceParams {
    public boolean enableMemory;
    public boolean disableStack;
    public boolean disableStorage;
    public boolean enableReturnData;

    public TraceParams() {
        enableMemory = true;
        disableStack = false;
        disableStorage = false;
        enableReturnData = true;
    }

    public TraceParams(boolean enableMemory, boolean disableStack, boolean disableStorage, boolean enableReturnData) {
        this.enableMemory = enableMemory;
        this.disableStack = disableStack;
        this.disableStorage = disableStorage;
        this.enableReturnData = enableReturnData;
    }
}
