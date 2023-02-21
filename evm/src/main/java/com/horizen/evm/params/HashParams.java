package com.horizen.evm.params;

import com.horizen.evm.JsonPointer;

public class HashParams extends JsonPointer {
    public final byte[][] values;

    public HashParams(byte[][] values) {
        this.values = values;
    }
}
