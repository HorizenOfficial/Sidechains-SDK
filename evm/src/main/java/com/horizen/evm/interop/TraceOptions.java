package com.horizen.evm.interop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TraceOptions {
    public final boolean enableMemory;
    public final boolean disableStack;
    public final boolean disableStorage;
    public final boolean enableReturnData;

    public TraceOptions() {
        enableMemory = true;
        disableStack = false;
        disableStorage = false;
        enableReturnData = true;
    }
}
