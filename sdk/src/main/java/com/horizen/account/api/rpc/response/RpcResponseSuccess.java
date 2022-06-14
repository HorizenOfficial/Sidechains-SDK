package com.horizen.account.api.rpc.response;

import com.horizen.api.http.SuccessResponse;

public class RpcResponseSuccess extends RpcResponse implements SuccessResponse {
    private final Object result;

    public RpcResponseSuccess(String id, Object result) {
        super(id);
        this.result = result;
    }

    public Object getResult() {
        return result;
    }
}
