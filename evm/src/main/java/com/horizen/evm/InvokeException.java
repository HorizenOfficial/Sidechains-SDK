package com.horizen.evm;

public class InvokeException extends RuntimeException {
    private final String error;
    private final String method;
    private final JsonPointer args;

    public InvokeException(String error, String method, JsonPointer args) {
        this.error = error;
        this.method = method;
        this.args = args;
    }

    @Override
    public String toString() {
        return String.format(
            "InvokeException{error='%s', method='%s', args=%s}",
            error,
            method,
            args == null ? null : args.toNative()
        );
    }
}
