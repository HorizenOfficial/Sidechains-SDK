package com.horizen.account.api.rpc.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizen.account.api.rpc.handler.RpcException;
import com.horizen.account.api.rpc.utils.RpcCode;
import com.horizen.account.api.rpc.utils.RpcError;

import java.util.ArrayList;
import java.util.List;

/**
 * [
 * {"id":1648039192785,"jsonrpc":"2.0","method":"eth_chainId","params":[]},
 * {"id":3918473928749,"jsonrpc":"2.0","method":"eth_blockNumber","params":[]}
 * ]
 */
public class RpcBatchRequest {

    private List<RpcRequest> rpcRequests;
    private int invalidRequestsNumber;

    public RpcBatchRequest(JsonNode json) throws RpcException {

        if (json.isArray() && json.isEmpty()) {
            throw new RpcException(RpcError.fromCode(RpcCode.ParseError));
        }

        List<RpcRequest> inputRpcRequests = new ArrayList<>();
        for(JsonNode jsonItem: json) {
            try {
                RpcRequest rpcRequest = new RpcRequest(jsonItem);
                inputRpcRequests.add(rpcRequest);
            } catch (RpcException e) {
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
