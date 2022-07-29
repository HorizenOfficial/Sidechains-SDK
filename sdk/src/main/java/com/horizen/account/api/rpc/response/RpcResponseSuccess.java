package com.horizen.account.api.rpc.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.horizen.api.http.SuccessResponse;

public class RpcResponseSuccess extends RpcResponse implements SuccessResponse {
    private final Object result;
    public RpcResponseSuccess(String id, Object result) {
        super(id);
        this.result = result;
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Object getResult() {
        return result;
    }
}
