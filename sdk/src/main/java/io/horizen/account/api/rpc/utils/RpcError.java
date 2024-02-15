package io.horizen.account.api.rpc.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.json.Views;

@JsonView(Views.Default.class)
public class RpcError {
    // allowed error codes: https://www.jsonrpc.org/specification#error_object
    public final int code;
    public final String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public final String data;

    public RpcError(int code, String message, String data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public RpcError(RpcCode code, String message, String data) {
        this(code.code, message, data);
    }

    public static RpcError fromCode(RpcCode code, String data) {
        String message = data == null ? code.message : code.message + ": " + data;
        return new RpcError(code.code, message, data);
    }

    public static RpcError fromCode(RpcCode code) {
        return fromCode(code, null);
    }

    @Override
    public String toString() {
        return String.format("RpcError{code=%d, message='%s', data='%s'}", code, message, data);
    }
}
