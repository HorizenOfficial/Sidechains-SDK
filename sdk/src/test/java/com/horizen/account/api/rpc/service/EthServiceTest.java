package com.horizen.account.api.rpc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizen.account.api.rpc.request.RpcRequest;
import com.horizen.account.api.rpc.response.RpcResponseError;
import com.horizen.account.api.rpc.utils.Quantity;
import com.horizen.account.node.AccountNodeView;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.RawTransaction;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;

import static org.junit.Assert.*;

public class EthServiceTest {
    RpcRequest request;
    EthService ethService;
    RawTransaction transaction;

    @Before
    public void BeforeEachTest() {
        AccountNodeView view = null;
        final BigInteger someValue = BigInteger.ONE;
        transaction = RawTransaction.createTransaction(someValue,
                someValue, someValue, "0x", someValue, "");
        ethService = new EthService(view);
    }

    @Test
    public void RpcHandlerTest() throws InvocationTargetException, IllegalAccessException, IOException {
        String json;
        ObjectMapper mapper = new ObjectMapper();
        JsonNode request;
        RpcRequest rpcRequest;

        // Test 1: Parameters are of wrong type
        json = "{\"id\":\"1\", \"jsonrpc\":\"2.0\",\"method\":\"eth_estimateGas\", \"params\":{\"tx\":\"test\", \"tx2\":\"test2\"}}";
        request = mapper.readTree(json);
        rpcRequest = new RpcRequest(request);
        assertEquals("Invalid params", ((RpcResponseError) ethService.execute(rpcRequest)).getError().getMessage());

        // Test 2: Wrong number of parameters
        json = "{\"id\":\"1\", \"jsonrpc\":\"2.0\",\"method\":\"eth_estimateGas\", \"params\":[5, 10, 20]}}";
        request = mapper.readTree(json);
        rpcRequest = new RpcRequest(request);
        assertEquals("Invalid params", ((RpcResponseError) ethService.execute(rpcRequest)).getError().getMessage());

        // Test 3: Request execution calls correct function and returns value correctly
        json = "{\"id\":\"1\", \"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\", \"params\":[]}}";
        request = mapper.readTree(json);
        rpcRequest = new RpcRequest(request);
        assertEquals("0x1337", ((Quantity) ethService.execute(rpcRequest)).getValue());
    }
}
