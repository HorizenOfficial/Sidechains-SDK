package com.horizen.evm.interop;

import com.horizen.evm.JsonPointer;
import com.horizen.evm.utils.Hash;

public class OpenStateParams extends JsonPointer {
    public Hash root;

    public OpenStateParams() {
    }

    public OpenStateParams(byte[] root) {
        this.root = Hash.FromBytes(root);
    }
}
