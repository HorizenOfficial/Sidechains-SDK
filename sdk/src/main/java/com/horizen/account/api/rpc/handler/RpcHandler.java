package com.horizen.account.api.rpc.handler;

import com.google.inject.Inject;
import com.horizen.account.api.rpc.request.RpcRequest;
import com.horizen.account.api.rpc.response.RpcResponseError;
import com.horizen.account.api.rpc.response.RpcResponseSuccess;
import com.horizen.account.api.rpc.service.EthService;
import com.horizen.account.api.rpc.utils.RpcCode;
import com.horizen.account.api.rpc.utils.RpcError;
import com.horizen.api.http.ApiResponse;

public class RpcHandler {
    private final EthService ethService;

    @Inject
    public RpcHandler(EthService ethService) {
        this.ethService = ethService;
    }

    public ApiResponse apply(RpcRequest request) {
        try {
            Object result = null;
            if (ethService.hasMethod(request.getMethod())) {
                result = ethService.execute(request);
            }
            if (result == null) {
                return new RpcResponseError(request.getId(), RpcError.fromCode(RpcCode.MethodNotFound));
            }
            return new RpcResponseSuccess(request.getId(), result);
        } catch (Exception e) {
            return new RpcResponseError(request.getId(), RpcError.fromCode(RpcCode.InternalError, e.getMessage()));
        }
    }
}
