package com.horizen.evm.interop;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TraceParams {
    public final boolean enableMemory;
    public final boolean disableStack;
    public final boolean disableStorage;
    public final boolean enableReturnData;

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
