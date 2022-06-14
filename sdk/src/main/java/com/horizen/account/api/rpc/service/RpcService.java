package com.horizen.account.api.rpc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizen.account.api.rpc.request.RpcRequest;
import com.horizen.account.api.rpc.response.RpcResponseError;
import com.horizen.account.api.rpc.utils.RpcCode;
import com.horizen.account.api.rpc.utils.RpcError;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

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

    public Object execute(RpcRequest req) throws InvocationTargetException, IllegalAccessException {
        var m = rpcMethods.get(req.getMethod());
        if (m == null) return new RpcResponseError(req.getId(), RpcError.fromCode(RpcCode.MethodNotFound));
        JsonNode params = req.getParams();
        if (!params.isArray()) return new RpcResponseError(req.getId(), RpcError.fromCode(RpcCode.InvalidParams));
        var parameters = m.getParameterTypes();
        if (parameters.length != params.size()) return new RpcResponseError(req.getId(), RpcError.fromCode(RpcCode.InvalidParams));
        var mapper = new ObjectMapper();
        Object[] convertedParams = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            convertedParams[i] = mapper.convertValue(params.get(i), parameters[i]);
        }
        return m.invoke(this, convertedParams);
    }
}
