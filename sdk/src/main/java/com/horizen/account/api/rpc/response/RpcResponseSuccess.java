package com.horizen.account.api.rpc.response;

public class RpcResponseSuccess extends RpcResponse {
    private final Object result;

    public RpcResponseSuccess(String id, Object result) {
        super(id);
        this.result = result;
    }

    public Object getResult() {
        return result;
    }
}
