package com.horizen.evm.interop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TracerConfig {

    private final boolean onlyTopCall;
    private final boolean withLog;

    public TracerConfig() {
        this.onlyTopCall = false;
        this.withLog = false;
    }

    public boolean isOnlyTopCall() {
        return onlyTopCall;
    }

    public boolean isWithLog() {
        return withLog;
    }
}
