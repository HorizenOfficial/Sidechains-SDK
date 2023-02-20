package com.horizen.evm.interop;

import com.horizen.evm.JsonPointer;

public class HashParams extends JsonPointer {
    public final byte[][] values;

    public HashParams(byte[][] values) {
        this.values = values;
    }
}
