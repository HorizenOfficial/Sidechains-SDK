package com.horizen.account.api.rpc.response;

import com.horizen.account.api.rpc.utils.RpcError;
import com.horizen.api.http.ErrorResponse;

import java.util.Optional;

public class RpcResponseError extends RpcResponse implements ErrorResponse {
    private final RpcError error;

    public RpcResponseError(String id, RpcError error) {
        super(id);
        this.error = error;
    }

    public RpcError getError() {
        return error;
    }

    @Override
    public String code() {
        return error.getData();
    }

    @Override
    public String description() {
        return error.getMessage();
    }

    @Override
    public Optional<Throwable> exception() {
        return Optional.empty();
    }
}
