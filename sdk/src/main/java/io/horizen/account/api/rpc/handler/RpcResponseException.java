package io.horizen.account.api.rpc.handler;

import io.horizen.account.api.rpc.request.RpcId;
import io.horizen.account.api.rpc.utils.RpcError;

public class RpcResponseException extends RpcException {
    public final RpcId id;

    public RpcResponseException(RpcError error, RpcId id) {
        super(error);
        this.id = id;
    }
}
