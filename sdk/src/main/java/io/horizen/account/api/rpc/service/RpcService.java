package io.horizen.account.api.rpc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.horizen.account.api.rpc.handler.RpcException;
import io.horizen.account.api.rpc.request.RpcRequest;
import io.horizen.account.api.rpc.utils.RpcCode;
import io.horizen.account.api.rpc.utils.RpcError;
import io.horizen.account.serialization.EthJsonMapper;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

public class RpcService {
    private final HashMap<String, Method> rpcMethods;
    private final ObjectMapper mapper;

    public RpcService() {
        rpcMethods = new HashMap<>();
        var methods = this.getClass().getDeclaredMethods();
        for (var method : methods) {
            var annotation = method.getAnnotation(RpcMethod.class);
            if (annotation == null) continue;
            rpcMethods.put(annotation.value(), method);
        }
        mapper = EthJsonMapper.getMapper();
    }

    public boolean hasMethod(String method) {
        return rpcMethods.containsKey(method);
    }

    private Object[] convertArgs(Method method, JsonNode args) throws RpcException {
        var optionalAnnotation = method.getAnnotation(RpcOptionalParameters.class);
        var optionalParameters = optionalAnnotation == null ? 0 : optionalAnnotation.value();
        var parameters = method.getParameterTypes();
        var argsCount = args == null ? 0 : args.size();
        if ((args != null && !args.isArray()) || argsCount > parameters.length ||
            argsCount < parameters.length - optionalParameters) {
            throw new RpcException(RpcError.fromCode(RpcCode.InvalidParams));
        }
        try {
            var convertedArgs = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                convertedArgs[i] = mapper.convertValue(args == null ? null : args.get(i), parameters[i]);
            }
            return convertedArgs;
        } catch (IllegalArgumentException err) {
            LogManager.getLogger().trace("RPC call with invalid params: " + method, err);
            throw new RpcException(RpcError.fromCode(RpcCode.InvalidParams, err.getMessage()));
        }
    }

    public Object execute(RpcRequest req) throws Throwable {
        var method = rpcMethods.get(req.method);
        if (method == null) throw new RpcException(RpcError.fromCode(RpcCode.MethodNotFound));
        var args = convertArgs(method, req.params);
        try {
            return method.invoke(this, args);
        } catch (InvocationTargetException e) {
            // unpack and rethrow potential RpcException
            LogManager.getLogger().trace("RPC call failed: " + method, e);
            throw e.getCause();
        } catch (IllegalArgumentException e) {
            LogManager.getLogger().trace("RPC call failed: " + method, e);
            throw e.getCause();
        }
    }
}
