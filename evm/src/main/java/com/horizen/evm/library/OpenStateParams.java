package com.horizen.evm.library;

public class OpenStateParams extends JsonPointer {
    public String root;

    public OpenStateParams() {
    }

    public OpenStateParams(String root) {
        this.root = root;
    }
}
