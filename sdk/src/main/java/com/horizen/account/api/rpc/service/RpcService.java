package com.horizen.account.api.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParamsType;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

public class RpcService {
    private final HashMap<String, Method> rpcMethods;

    public RpcService() {
        rpcMethods = new HashMap<>();
        var methods = this.getClass().getDeclaredMethods();
        for (var method : methods) {
            var annotation = method.getAnnotation(RpcMethod.class);
            if (annotation == null) continue;
            rpcMethods.put(annotation.value(), method);
        }
    }

    public boolean hasMethod(String method) {
        return rpcMethods.containsKey(method);
    }

    public Object execute(JSONRPC2Request req) throws InvocationTargetException, IllegalAccessException {
        var m = rpcMethods.get(req.getMethod());
        if (m == null)
            return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());
        if (req.getParamsType() != JSONRPC2ParamsType.ARRAY)
            throw new UnsupportedOperationException("params must be an array");
        List params = req.getPositionalParams();
        var parameters = m.getParameterTypes();
        if (parameters.length != params.size()) throw new UnsupportedOperationException("invalid number of params");
        var mapper = new ObjectMapper();
        Object[] convertedParams = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            convertedParams[i] = mapper.convertValue(params.get(i), parameters[i]);
        }
        return m.invoke(this, convertedParams);
    }
}
