package com.horizen.evm.interop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TraceOptions {
    public final boolean enableMemory;
    public final boolean disableStack;
    public final boolean disableStorage;
    public final boolean enableReturnData;

    //    Default values:
    //    enableMemory = true;
    //    disableStack = false;
    //    disableStorage = false;
    //    enableReturnData = true;
    public TraceOptions(boolean enableMemory, boolean disableStack, boolean disableStorage, boolean enableReturnData) {
        this.enableMemory = enableMemory;
        this.disableStack = disableStack;
        this.disableStorage = disableStorage;
        this.enableReturnData = enableReturnData;
    }
}
