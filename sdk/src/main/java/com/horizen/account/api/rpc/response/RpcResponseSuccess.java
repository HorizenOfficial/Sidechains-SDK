package com.horizen.account.api.rpc.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.horizen.api.http.SuccessResponse;

public class RpcResponseSuccess extends RpcResponse implements SuccessResponse {
    protected final Object result;

    public RpcResponseSuccess(long id, Object result) {
        super(id);
        this.result = result;
    }

    @JsonInclude()
    public Object getResult() {
        return result;
    }

    @Override
    public String toString() {
        return String.format("RpcResponseSuccess{jsonrpc='%s', id='%s', result=%s}", jsonrpc, id, result);
    }
}
