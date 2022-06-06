package com.horizen.account.api.rpc.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class Data {
    private final byte[] value;

    @JsonCreator
    public Data(byte[] value) {
        this.value = value;
    }

    @JsonValue
    public byte[] getValue() {
        return value;
    }
}
