package com.horizen.evm.params;

import com.horizen.evm.JsonPointer;

public class LevelDBParams extends JsonPointer {
    public final String path;

    public LevelDBParams(String path) {
        this.path = path;
    }
}
