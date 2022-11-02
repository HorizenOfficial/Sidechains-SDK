package com.horizen.account.api.rpc.request;

import com.fasterxml.jackson.databind.JsonNode;

public class RpcId {

    private Long longId;
    private String stringId;

    public RpcId(JsonNode jsonId) {

        // manage numeric id input
        if(jsonId.canConvertToLong()){
            if(jsonId.asLong()>=0)
                this.longId = jsonId.asLong();
            else
                throw new IllegalStateException("Rpc Id can't be a negative number");
        } else if(jsonId.isNull())
            throw new IllegalStateException("Rpc Id can't be null");
        else
            this.stringId = jsonId.asText();
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
