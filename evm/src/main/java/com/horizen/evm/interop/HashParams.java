package com.horizen.evm.interop;

import com.horizen.evm.JsonPointer;

public class HashParams extends JsonPointer {
    public byte[][] values;

    public HashParams() {
    }

    public HashParams(byte[][] values) {
        this.values = values;
    }
}
