package com.horizen.account.api.rpc.utils;

public enum RpcCode {
    // default json-rpc error codes
    ParseError(-32700, "Parse error"),
    InvalidRequest(-32600, "Invalid request"),
    MethodNotFound(-32601, "Method not found"),
    InvalidParams(-32602, "Invalid params"),
    InternalError(-32603, "Internal error"),
    UnknownBlock(-39001, "Unknown block"),

    // custom ethereum error codes
    Unauthorized(1, "Unauthorized"),
    ActionNotAllowed(2, "Action not allowed"),
    ExecutionError(3, "Execution error");

    private final int code;
    private final String message;

    RpcCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
