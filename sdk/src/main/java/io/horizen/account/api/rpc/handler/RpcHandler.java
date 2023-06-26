package io.horizen.account.api.rpc.handler;

import io.horizen.account.api.rpc.request.RpcRequest;
import io.horizen.account.api.rpc.response.RpcResponseError;
import io.horizen.account.api.rpc.response.RpcResponseSuccess;
import io.horizen.account.api.rpc.service.RpcService;
import io.horizen.account.api.rpc.utils.RpcCode;
import io.horizen.account.api.rpc.utils.RpcError;
import io.horizen.api.http.ApiResponse;

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
            return new RpcResponseError(request.id, e.error);
        } catch (Throwable e) {
            return new RpcResponseError(request.id, RpcError.fromCode(RpcCode.InternalError, e.getMessage()));
        }
    }

}
