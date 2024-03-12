package io.horizen.account.api.rpc.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.horizen.account.api.rpc.handler.RpcResponseException;
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

    private static final String mandatoryIdField = "id";
    private static final List<String> otherMandatoryFields = List.of("jsonrpc", "method");
    private static final List<String> stringFields = List.of("jsonrpc", "method");
    private static final String JSON_RPC_VERSION = "2.0";

    public RpcRequest(JsonNode json) throws RpcResponseException {

        if (json.isArray() && json.isEmpty()) {
            throw new RpcResponseException(RpcError.fromCode(RpcCode.InvalidRequest, "Empty array as input"), new RpcId());
        }

        // This should be the first to be checked, otherwise we fail to return the request id in other error conditions
        if (!json.has(mandatoryIdField)) {
            throw new RpcResponseException(RpcError.fromCode(RpcCode.InvalidRequest, String.format("missing field: %s", mandatoryIdField)), new RpcId());
        }

        try {
            id = new RpcId(json.get(mandatoryIdField));
        } catch (IllegalArgumentException e) {
            throw new RpcResponseException(RpcError.fromCode(RpcCode.InvalidRequest, e.getMessage()), new RpcId());
        }

        for (var field : otherMandatoryFields) {
            if (!json.has(field)) {
                throw new RpcResponseException(
                    RpcError.fromCode(RpcCode.InvalidRequest, String.format("missing field: %s", field)), id);
            }
        }

        for (var field : stringFields) {
            if (!json.get(field).isTextual()) {
                throw new RpcResponseException(
                    RpcError.fromCode(RpcCode.InvalidRequest, String.format("field must be string: %s", field)), id);
            }
        }

        jsonrpc = json.get("jsonrpc").asText();
        // check if the jsonrpc value has the correct version
        if(!jsonrpc.equals(JSON_RPC_VERSION)) {
            throw new RpcResponseException(RpcError.fromCode(RpcCode.InvalidRequest, "jsonrpc value is not valid"), id);
        }

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
