package com.horizen.evm.interop;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TraceParams {
    @JsonProperty("enableMemory")
    final boolean enableMemory;
    @JsonProperty("disableStack")
    final boolean disableStack;
    @JsonProperty("disableStorage")
    public boolean disableStorage;
    @JsonProperty("enableReturnData")
    public boolean enableReturnData;

    @JsonCreator()
    public TraceParams() {
        enableMemory = true;
        disableStack = false;
        disableStorage = false;
        enableReturnData = true;
    }

    @JsonCreator()
    public TraceParams(boolean enableMemory, boolean disableStack, boolean disableStorage, boolean enableReturnData) {
        this.enableMemory = enableMemory;
        this.disableStack = disableStack;
        this.disableStorage = disableStorage;
        this.enableReturnData = enableReturnData;
    }
}
