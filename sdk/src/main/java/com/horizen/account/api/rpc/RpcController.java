package com.horizen.account.api.rpc;

import akka.http.javadsl.server.Route;
import com.google.inject.Inject;
import com.horizen.account.api.ControllerBase;
import com.horizen.account.api.rpc.handler.RpcHandler;
import com.horizen.account.api.rpc.request.RpcRequest;

import java.util.List;

public class RpcController extends ControllerBase {
    private static final String CONTROLLER_TAG = "eth_v1";
    private static final String RPC_PATH = "rpc";
    private final RpcHandler rpcHandler;

    @Inject
    public RpcController(
            RpcHandler rpcHandler
    ) {
        super(CONTROLLER_TAG);
        this.rpcHandler = rpcHandler;
    }

    public List<Route> getRoutes() {
        return List.of(
                bindPostRequestSubPath(rpcHandler, RpcRequest.class, RPC_PATH)
        );
    }
}
