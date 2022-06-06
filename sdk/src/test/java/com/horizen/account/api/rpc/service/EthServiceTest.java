package com.horizen.account.api.rpc.handler;

import com.horizen.account.api.rpc.service.EthService;
import com.horizen.account.api.rpc.service.Quantity;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.RawTransaction;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RpcHandlerTest {
    JSONRPC2Request request;
    EthService ethService;
    RawTransaction transaction;

    @Before
    public void BeforeEachTest() {
        final BigInteger someValue = BigInteger.ONE;
        transaction = RawTransaction.createTransaction(someValue,
                someValue, someValue, "0x", someValue, "");
        List params = new LinkedList();
        params.add(transaction);
        params.add(new Quantity("latest"));
        request = new JSONRPC2Request("eth_estimateGas", params, "1");
        ethService = new EthService();
    }

    @Test
    public void RpcHandlerTest() throws InvocationTargetException, IllegalAccessException {
        // Test 1: Request execution calls correct function and returns value correctly
        assertEquals("estimateGas expectected to return 0x1",
                new Quantity("0x1").getValue(),
                ((Quantity) ethService.execute(request)).getValue());
    }
}
