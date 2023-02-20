package com.horizen.evm.interop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TraceOptions {
    public final boolean enableMemory;
    public final boolean disableStack;
    public final boolean disableStorage;
    public final boolean enableReturnData;

    /**
     * Name of the tracer to use, e.g. "callTracer" or "4byteTracer".
     */
    public final String tracer;

    /**
     * Tracer configuration as raw JSON.
     */
    public final JsonNode tracerConfig;

    public TraceOptions() {
        enableMemory = false;
        disableStack = false;
        disableStorage = false;
        enableReturnData = false;
        tracer = null;
        tracerConfig = null;
    }
}
