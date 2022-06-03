package com.horizen.evm.library;

import com.horizen.evm.utils.Hash;

public class OpenStateParams extends JsonPointer {
    public Hash root;

    public OpenStateParams() {
    }

    public OpenStateParams(byte[] root) {
        this.root = new Hash(root);
    }
}
