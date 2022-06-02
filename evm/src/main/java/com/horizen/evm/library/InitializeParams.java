package com.horizen.evm.library;

public class InitializeParams extends JsonPointer {
    public String path;

    public InitializeParams() {
    }

    public InitializeParams(String path) {
        this.path = path;
    }
}
