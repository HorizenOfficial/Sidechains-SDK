package io.horizen.evm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

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

    public TraceOptions(
        @JsonProperty("enableMemory") boolean enableMemory,
        @JsonProperty("disableStack") boolean disableStack,
        @JsonProperty("disableStorage") boolean disableStorage,
        @JsonProperty("enableReturnData") boolean enableReturnData,
        @JsonProperty("tracer") String tracer,
        @JsonProperty("tracerConfig") JsonNode tracerConfig
    ) {
        this.enableMemory = enableMemory;
        this.disableStack = disableStack;
        this.disableStorage = disableStorage;
        this.enableReturnData = enableReturnData;
        this.tracer = tracer;
        this.tracerConfig = tracerConfig;
    }
}
