package com.horizen.api.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("ScTimestamp")
public class ScTimestamp {

    @JsonProperty("scTimestamp_value")
    private long timestamp;

    public ScTimestamp(){

    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
