package com.horizen.account.api.rpc.request;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {"id":1648039192785,"jsonrpc":"2.0","method":"eth_chainId","params":[]}
 */
public class RpcRequest {
    private String jsonrpc;
    private long id;
    private String method;
    private JsonNode params;

    public RpcRequest() {}

    public RpcRequest(JsonNode json) {
        this.jsonrpc = json.get("jsonrpc").asText();
        this.id = json.get("id").asLong();
        this.method = json.get("method").asText();
        this.params = json.get("params");
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
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

    public void setParams(JsonNode params) {
        this.params = params;
    }

    @Override
    public String toString() {
        return String.format("RpcRequest{jsonrpc='%s', id='%s', method='%s', params=%s}", jsonrpc, id, method, params);
    }
}
