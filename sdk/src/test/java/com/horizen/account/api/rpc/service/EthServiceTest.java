package com.horizen.account.api.rpc.service;

import com.horizen.account.transaction.EthereumTransaction;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParamsType;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.RawTransaction;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class EthServiceTest {
    JSONRPC2Request request;
    EthService ethService;
    RawTransaction transaction;

    @Before
    public void BeforeEachTest() {
        final BigInteger someValue = BigInteger.ONE;
        transaction = RawTransaction.createTransaction(someValue,
                someValue, someValue, "0x", someValue, "");
        ethService = new EthService();
    }

    @Test
    public void RpcHandlerTest() throws InvocationTargetException, IllegalAccessException {
        List params = new LinkedList();
        boolean exceptionOccurred = false;

        // Test 1: Parameters are of wrong type
        Map<String,Object> namedParams = new HashMap<String,Object>();
        namedParams.put("test", "test");
        request = new JSONRPC2Request("eth_estimateGas", namedParams, "1");
        try {
            ethService.execute(request);
        } catch (UnsupportedOperationException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test1: Exception during request execution expected.", exceptionOccurred);

        exceptionOccurred = false;
        // Test 2: Wrong number of parameters
        request = new JSONRPC2Request("eth_estimateGas", params, "1");
        try {
            ethService.execute(request);
        } catch (UnsupportedOperationException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test2: Exception during request execution expected.", exceptionOccurred);

        // Test 3: Request execution calls correct function and returns value correctly
        params.add(transaction);
        params.add(new Quantity("latest"));
        assertEquals("estimateGas expectected to return 0x1",
                new Quantity("0x1").getValue(),
                ((Quantity) ethService.execute(request)).getValue());
    }
}
