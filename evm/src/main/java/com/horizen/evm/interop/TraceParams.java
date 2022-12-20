package com.horizen.evm.interop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TraceParams {
    private final boolean enableMemory;
    private final boolean disableStack;
    private final boolean disableStorage;
    private final boolean enableReturnData;
    private final String tracer;
    private final String timeout;
    private final TracerConfig tracerConfig;

    public TraceParams() {
        enableMemory = false;
        disableStack = false;
        disableStorage = false;
        enableReturnData = false;
        tracer = null;
        timeout = null;
        tracerConfig = null;
    }

    public boolean isEnableMemory() {
        return enableMemory;
    }

    public boolean isDisableStack() {
        return disableStack;
    }

    public boolean isDisableStorage() {
        return disableStorage;
    }

    public boolean isEnableReturnData() {
        return enableReturnData;
    }

    public String getTracer() {
        return tracer;
    }

    public String getTimeout() {
        return timeout;
    }

    public TracerConfig getTracerConfig() {
        return tracerConfig;
    }
}
