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
            if (rpcService.hasMethod(request.method)) {
                var result = rpcService.execute(request);
                return new RpcResponseSuccess(request.id, result);
            }
            return new RpcResponseError(request.id, RpcError.fromCode(RpcCode.MethodNotFound));
        } catch (RpcException e) {
            if (e.error.code == RpcCode.UnknownBlock.code) return new RpcResponseSuccess(request.id, null);
            return new RpcResponseError(request.id, e.error);
        } catch (Throwable e) {
            return new RpcResponseError(request.id, RpcError.fromCode(RpcCode.InternalError, e.getMessage()));
        }
    }

}
