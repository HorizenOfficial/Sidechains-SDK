package com.horizen.account.api.rpc.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
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
        // do not serialize null or empty values
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return mapper.writeValueAsString(object);
    }
}
