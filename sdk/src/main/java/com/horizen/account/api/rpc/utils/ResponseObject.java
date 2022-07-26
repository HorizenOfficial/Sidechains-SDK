package com.horizen.account.api.rpc.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ResponseObject {
    private final Object object;

    @JsonCreator
    public ResponseObject(Object reqObj) {
        this.object = reqObj;
    }

    @JsonValue
    public String getValue() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(object);
    }
}
