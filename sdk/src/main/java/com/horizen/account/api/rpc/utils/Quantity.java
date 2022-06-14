package com.horizen.account.api.rpc.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class Quantity {
    private final String value;

    @JsonCreator
    public Quantity(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
