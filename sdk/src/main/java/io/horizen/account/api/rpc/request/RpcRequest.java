package io.horizen.account.api.rpc.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.horizen.account.api.rpc.handler.RpcException;
import io.horizen.account.api.rpc.utils.RpcCode;
import io.horizen.account.api.rpc.utils.RpcError;

import java.util.List;

/**
 * {"id":1648039192785,"jsonrpc":"2.0","method":"eth_chainId","params":[]}
 */
public class RpcRequest {
    public final String jsonrpc;
    public final RpcId id;
    public final String method;
    public final JsonNode params;

    private static final List<String> mandatoryFields = List.of("jsonrpc", "id", "method");
    private static final List<String> stringFields = List.of("jsonrpc", "method");

    public RpcRequest(JsonNode json) throws RpcException {
        for (var field : mandatoryFields) {
            if (!json.has(field)) {
                throw new RpcException(
                    RpcError.fromCode(RpcCode.InvalidRequest, String.format("missing field: %s", field)));
            }
        }
        for (var field : stringFields) {
            if (!json.get(field).isTextual()) {
                throw new RpcException(
                    RpcError.fromCode(RpcCode.InvalidRequest, String.format("field must be string: %s", field)));
            }
        }
        try {
            id = new RpcId(json.get("id"));
        } catch (IllegalArgumentException e) {
            throw new RpcException(RpcError.fromCode(RpcCode.InvalidRequest, e.getMessage()));
        }
        jsonrpc = json.get("jsonrpc").asText();
        method = json.get("method").asText();
        // params might be null, which is allowed
        params = json.get("params");
    }

    @Override
    public String toString() {
        return String.format(
            "RpcRequest={jsonrpc='%s', id='%s', method='%s', params=%s}", jsonrpc, id.toString(), method, params);
    }
}
