package com.horizen.account.api.rpc.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.Views;

@JsonView(Views.Default.class)
public class RpcError {
    // allowed error codes: https://www.jsonrpc.org/specification#error_object
    private final int code;
    private final String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String data;

    public RpcError(int code, String message, String data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public RpcError(RpcCode code, String message, String data) {
        this(code.getCode(), message, data);
    }

    public static RpcError fromCode(RpcCode code, String data) {
        return new RpcError(code.getCode(), code.getMessage(), data);
    }

    public static RpcError fromCode(RpcCode code) {
        return fromCode(code, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getData() {
        return data;
    }

    @Override
    public String toString() {
        return String.format("RpcError{code=%d, message='%s', data='%s'}", code, message, data);
    }
}
