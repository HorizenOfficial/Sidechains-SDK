package com.horizen.evm.interop;

import com.horizen.evm.JsonPointer;

public class LevelDBParams extends JsonPointer {
    public final String path;

    public LevelDBParams(String path) {
        this.path = path;
    }
}
