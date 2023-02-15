package com.horizen.account.api.rpc.handler;

import com.horizen.account.api.rpc.utils.RpcError;

public class RpcException extends Throwable {
    public RpcError error;

    public RpcException(RpcError error) {
        this.error = error;
    }
}
