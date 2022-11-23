package com.horizen.account.api.rpc.handler;

import com.horizen.account.api.rpc.request.RpcRequest;
import com.horizen.account.api.rpc.response.RpcResponseError;
import com.horizen.account.api.rpc.response.RpcResponseSuccess;
import com.horizen.account.api.rpc.service.RpcService;
import com.horizen.account.api.rpc.utils.RpcCode;
import com.horizen.account.api.rpc.utils.RpcError;
import com.horizen.api.http.ApiResponse;

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

}
