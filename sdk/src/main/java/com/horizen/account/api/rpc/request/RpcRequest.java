package com.horizen.account.api.rpc.request;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {"id":"1648039192785","jsonrpc":"2.0","method":"eth_chainId","params":[]}
 */
public class RpcRequest {
    private String jsonrpc;
    private String method;
    private JsonNode params;
    private String id;

    public RpcRequest() {}

    public RpcRequest(JsonNode json) {
        this.jsonrpc = json.get("jsonrpc").asText();
        this.method = json.get("method").asText();
        this.params = json.get("params");
        this.id = json.get("id").asText();
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
