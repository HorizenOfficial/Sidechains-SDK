package com.horizen.account.api.rpc.request;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * [
 * {"id":1648039192785,"jsonrpc":"2.0","method":"eth_chainId","params":[]},
 * {"id":3918473928749,"jsonrpc":"2.0","method":"eth_chainId","params":[]}
 * ]
 */
public class RpcBatchRequest {

    private List<RpcRequest> rpcRequests;
    private int invalidRequestsNumber;

    public RpcBatchRequest() {}

    public RpcBatchRequest(JsonNode json) {
        // TODO create a RpcRequest element and push it to the batch list
        List<RpcRequest> inputRpcRequests = new ArrayList<>();
        for(JsonNode jsonItem: json) {
            try {
                RpcRequest rpcRequest = new RpcRequest(jsonItem);
                inputRpcRequests.add(rpcRequest);
            } catch (Exception e) {
                invalidRequestsNumber++;
            }
        }
        this.rpcRequests = inputRpcRequests;
    }

    public List<RpcRequest> getRpcRequests() {
        return rpcRequests;
    }

    public int getInvalidRequestsNumber() {
        return invalidRequestsNumber;
    }

    @Override
    public String toString() {
        return "RpcBatchRequest{" +
                "rpcRequests=" + rpcRequests +
                '}';
    }
}
