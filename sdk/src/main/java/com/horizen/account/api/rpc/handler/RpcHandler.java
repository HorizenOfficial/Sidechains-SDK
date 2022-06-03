package com.horizen.account.api.rpc.handler;

import com.google.inject.Inject;
import com.horizen.account.api.rpc.response.RpcResponse;
import com.horizen.account.api.rpc.service.EthService;
import com.horizen.api.http.ApiResponse;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.horizen.node.SidechainNodeView;

import java.util.function.BiFunction;

public class RpcHandler implements BiFunction<SidechainNodeView, JSONRPC2Request, ApiResponse> {
    private final EthService ethService;

    @Inject
    public RpcHandler(EthService ethService) {
        this.ethService = ethService;
    }

    @Override
    public ApiResponse apply(SidechainNodeView sidechainNodeView, JSONRPC2Request request) {
        try {
            Object result = null;
            if (ethService.hasMethod(request.getMethod())) {
                result = ethService.execute(request);
            }
            if (result == null) {
                return new RpcResponse(JSONRPC2Error.METHOD_NOT_FOUND, request.getID());
            }
            return new RpcResponse(result, request.getID());
        } catch (Exception e) {
            return new RpcResponse(JSONRPC2Error.INTERNAL_ERROR, request.getID());
        }
    }
}
