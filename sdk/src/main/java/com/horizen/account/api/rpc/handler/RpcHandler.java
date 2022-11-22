package com.horizen.account.api.rpc.handler;

import com.horizen.account.api.rpc.request.RpcBatchRequest;
import com.horizen.account.api.rpc.request.RpcId;
import com.horizen.account.api.rpc.request.RpcRequest;
import com.horizen.account.api.rpc.response.RpcResponse;
import com.horizen.account.api.rpc.response.RpcResponseError;
import com.horizen.account.api.rpc.response.RpcResponseSuccess;
import com.horizen.account.api.rpc.service.RpcService;
import com.horizen.account.api.rpc.utils.RpcCode;
import com.horizen.account.api.rpc.utils.RpcError;
import com.horizen.api.http.ApiResponse;

import java.util.ArrayList;
import java.util.List;

public class RpcHandler {
    private final RpcService rpcService;

    public RpcHandler(RpcService rpcService) {
        this.rpcService = rpcService;
    }

    public ApiResponse apply(RpcRequest request) {
        try {
            if (rpcService.hasMethod(request.getMethod())) {
                var result = rpcService.execute(request);
                return new RpcResponseSuccess(request.getId(), result);
            }
            return new RpcResponseError(request.getId(), RpcError.fromCode(RpcCode.MethodNotFound));
        } catch (RpcException e) {
            return new RpcResponseError(request.getId(), e.error);
        } catch (Throwable e) {
            return new RpcResponseError(request.getId(), RpcError.fromCode(RpcCode.InternalError, e.getMessage()));
        }
    }

    public List<RpcResponse> apply(RpcBatchRequest request) {

        final List<RpcResponse> responseList = new ArrayList<>();

        for(RpcRequest rpcRequest: request.getRpcRequests()) {
            try {
                if (rpcService.hasMethod(rpcRequest.getMethod())) {
                    var result = rpcService.execute(rpcRequest);
                    responseList.add(new RpcResponseSuccess(rpcRequest.getId(), result));
                } else
                    responseList.add(new RpcResponseError(rpcRequest.getId(), RpcError.fromCode(RpcCode.MethodNotFound)));
            } catch (RpcException e) {
                responseList.add(new RpcResponseError(rpcRequest.getId(), e.error));
            } catch (Throwable e) {
                responseList.add(new RpcResponseError(rpcRequest.getId(), RpcError.fromCode(RpcCode.InternalError, e.getMessage())));
            }
        }

        // add invalid requests to the batch response
        for(int i=0; i<request.getInvalidRequestsNumber(); i++)
            responseList.add(new RpcResponseError(new RpcId(), RpcError.fromCode(RpcCode.InvalidRequest, null)));

        return responseList;
    }
}
