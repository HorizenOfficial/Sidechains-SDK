package com.horizen.account.api.rpc.request;

import com.fasterxml.jackson.databind.JsonNode;

public class RpcId {

    private Long longId;
    private String stringId;

    public RpcId() {}

    public RpcId(JsonNode jsonId) {
        if(!jsonId.isNull()) {
            if(jsonId.isNumber()) {
                if(jsonId.canConvertToLong()) {
                    if(jsonId.asLong()>=0)
                        this.longId = jsonId.asLong();
                    else
                        throw new IllegalStateException("Rpc Id can't be a negative number");
                } else
                    throw new IllegalStateException("Rpc Id value is greater than datatype max value");
            } else
                this.stringId = jsonId.asText();
        } else
            throw new IllegalStateException("Rpc Id can't be null");
    }

    public Long getLongId() {
        return longId;
    }

    public String getStringId() {
        return stringId;
    }

    @Override
    public String toString() {
        if(longId!=null) return longId.toString();
        else if(stringId!=null) return stringId;
        else return null;
    }
}
