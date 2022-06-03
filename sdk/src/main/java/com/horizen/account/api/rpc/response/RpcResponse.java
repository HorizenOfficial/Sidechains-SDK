package com.horizen.account.api.rpc.response;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.api.http.ApiResponse;
import com.horizen.serialization.Views;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

@JsonView(Views.Default.class)
public class RpcResponse implements ApiResponse {
    JSONRPC2Response response;

    public RpcResponse(Object result, Object id) {
        response = new JSONRPC2Response(result, id);
    }

    public JSONRPC2Response getResponse() {
        return response;
    }
}