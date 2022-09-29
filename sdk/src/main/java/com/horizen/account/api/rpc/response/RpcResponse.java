package com.horizen.account.api.rpc.response;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.api.http.ApiResponse;
import com.horizen.serialization.Views;

@JsonView(Views.Default.class)
public abstract class RpcResponse implements ApiResponse {
    protected final String jsonrpc;
    protected final String id;

    public RpcResponse(String id) {
        this.jsonrpc = "2.0";
        this.id = id;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public String getId() {
        return id;
    }
}
