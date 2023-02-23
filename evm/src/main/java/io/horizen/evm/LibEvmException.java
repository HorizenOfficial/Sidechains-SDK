package io.horizen.evm;

public class LibEvmException extends RuntimeException {
    public LibEvmException(String error, String method, String args) {
        super(String.format("Error: \"%s\" occurred for method %s, with arguments %s", error, method, args));
    }
}
