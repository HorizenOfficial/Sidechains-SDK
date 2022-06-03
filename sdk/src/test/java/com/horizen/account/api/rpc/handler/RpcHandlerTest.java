package com.horizen.account.api.rpc.handler;

import com.horizen.account.api.rpc.handler.RpcHandler;
import com.horizen.account.api.rpc.service.EthService;
import com.horizen.account.api.rpc.service.Quantity;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;

public class RpcHandlerTest {
    JSONRPC2Request request;
    EthService ethService;
    RpcHandler rpcHandler;

    @Before
    public void BeforeEachTest() {
        List params = new LinkedList();
        params.add("test");
        params.add("test");
        request = new JSONRPC2Request("eth_getBalance", params, "01");
        ethService = new EthService();
    }

    @Test
    public void RpcHandlerTest() throws InvocationTargetException, IllegalAccessException {
        Quantity test = (Quantity) ethService.execute(request);
    }
}
