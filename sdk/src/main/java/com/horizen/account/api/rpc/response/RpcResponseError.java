package com.horizen.account.api.rpc.response;

import com.horizen.account.api.rpc.utils.RpcError;

public class RpcResponseError extends RpcResponse {
    private final RpcError error;

    public RpcResponseError(String id, RpcError error) {
        super(id);
        this.error = error;
    }

    public RpcError getError() {
        return error;
    }
}
