package com.horizen.account.api.rpc.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.horizen.account.api.rpc.request.RpcId;
import com.horizen.api.http.ApiResponse;
import com.horizen.json.serializer.RpcIdSerializer;
import com.horizen.json.Views;

@JsonView(Views.Default.class)
public abstract class RpcResponse implements ApiResponse {
    @JsonProperty("jsonrpc")
    protected final String jsonrpc = "2.0";

    @JsonProperty("id")
    @JsonSerialize(using = RpcIdSerializer.class)
    protected final RpcId id;

    public RpcResponse(RpcId id) {
        this.id = id;
    }
}
