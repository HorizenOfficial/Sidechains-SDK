package com.horizen.account.api.rpc.utils;

public enum RpcCode {
    // default json-rpc error codes
    ParseError(-32700, "Parse error"),
    InvalidRequest(-32600, "Invalid request"),
    MethodNotFound(-32601, "Method not found"),
    InvalidParams(-32602, "Invalid params"),
    InternalError(-32603, "Internal error"),

    // custom ethereum error codes
    Unauthorized(1, "Unauthorized"),
    ActionNotAllowed(2, "Action not allowed"),
    ExecutionError(3, "Execution error"),

    // the range of -32000 to -32099 is reserved for implementation-defined server-errors
    UnknownBlock(-32000, "Unknown block");

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
