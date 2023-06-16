package io.horizen.account.api.rpc.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.horizen.account.api.rpc.request.RpcId;
import io.horizen.account.json.serializer.RpcIdSerializer;
import io.horizen.api.http.ApiResponse;
import io.horizen.json.Views;

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
