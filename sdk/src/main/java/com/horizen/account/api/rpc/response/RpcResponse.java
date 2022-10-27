package com.horizen.account.api.rpc.response;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.api.http.ApiResponse;
import com.horizen.serialization.Views;

@JsonView(Views.Default.class)
public abstract class RpcResponse implements ApiResponse {
    protected final String jsonrpc;
    protected final long id;

    public RpcResponse(long id) {
        this.jsonrpc = "2.0";
        this.id = id;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public long getId() {
        return id;
    }
}
