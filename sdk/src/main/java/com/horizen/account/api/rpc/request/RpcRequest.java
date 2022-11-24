package com.horizen.account.api.rpc.request;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.horizen.account.api.rpc.handler.RpcException;
import com.horizen.account.api.rpc.utils.RpcCode;
import com.horizen.account.api.rpc.utils.RpcError;

import java.util.ArrayList;
import java.util.List;

/**
 * {"id":1648039192785,"jsonrpc":"2.0","method":"eth_chainId","params":[]}
 */
public class RpcRequest {
    private String jsonrpc;
    private RpcId id;
    private String method;
    private JsonNode params;

    public RpcRequest(JsonNode json) throws RpcException {
        List<String> keys = new ArrayList<>();
        var iterator = json.fieldNames();
        iterator.forEachRemaining(e -> keys.add(e));

        if (!keys.containsAll(List.of("jsonrpc", "id", "method"))) {
            throw new RpcException(RpcError.fromCode(RpcCode.ParseError));
        }

        try {
            this.id = new RpcId(json.get("id"));
        } catch (IllegalStateException e) {
            throw new RpcException(RpcError.fromCode(RpcCode.ParseError));
        }

        this.jsonrpc = json.get("jsonrpc").asText();
        this.method = json.get("method").asText();
        this.params = json.get("params");
    }

    @JsonGetter
    public String getJsonrpc() {
        return jsonrpc;
    }

    @JsonGetter
    public RpcId getId() {
        return id;
    }

    public void setId(RpcId id) {
        this.id = id;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public JsonNode getParams() {
        return params;
    }

    @Override
    public String toString() {
        return String.format("RpcRequest={jsonrpc='%s', id='%s', method='%s', params=%s}", jsonrpc, id.toString(), method, params);
    }
}
