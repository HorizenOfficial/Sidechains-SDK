package com.horizen.account.api.rpc.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.horizen.account.api.rpc.request.RpcId;
import com.horizen.api.http.ApiResponse;
import com.horizen.serialization.RpcIdSerializer;
import com.horizen.serialization.Views;

@JsonView(Views.Default.class)
public abstract class RpcResponse implements ApiResponse {
    protected final String jsonrpc;

    @JsonProperty("id")
    @JsonSerialize(using = RpcIdSerializer.class)
    protected final RpcId id;

    public RpcResponse(RpcId id) {
        this.jsonrpc = "2.0";
        this.id = id;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public RpcId getId() {
        return id;
    }
}
