package com.horizen.account.api.rpc.request;

import com.fasterxml.jackson.databind.JsonNode;

public class RpcId {

    private Long longId;
    private String stringId;

    public RpcId() {}

    public RpcId(JsonNode jsonId) {
        switch (jsonId.getNodeType()) {
            case STRING:
                this.stringId = jsonId.asText();
                break;
            case NUMBER:
                if (!jsonId.canConvertToLong()) {
                    throw new IllegalArgumentException("Rpc Id value is greater than datatype max value");
                }
                if (jsonId.asLong() < 0) {
                    throw new IllegalArgumentException("Rpc Id can't be a negative number");
                }
                this.longId = jsonId.asLong();
                break;
            case NULL:
                throw new IllegalArgumentException("Rpc Id can't be null");
            default:
                throw new IllegalArgumentException("Rpc Id is of invalid type");
        }
    }

    public RpcId(String stringId) {
        this.stringId = stringId;
    }

    public Long getLongId() {
        return longId;
    }

    public String getStringId() {
        return stringId;
    }

    @Override
    public String toString() {
        if (stringId != null) return stringId;
        if (longId != null) return longId.toString();
        return null;
    }
}
