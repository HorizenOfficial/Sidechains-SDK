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
            if (ethService.hasMethod(request.getMethod())) {
                var result = ethService.execute(request);
                return new RpcResponseSuccess(request.getId(), result);
            }
            return new RpcResponseError(request.getId(), RpcError.fromCode(RpcCode.MethodNotFound));
        } catch (RpcException e) {
            return new RpcResponseError(request.getId(), e.error);
        } catch (Exception e) {
            return new RpcResponseError(request.getId(), RpcError.fromCode(RpcCode.InternalError, e.getMessage()));
        }
    }
}
