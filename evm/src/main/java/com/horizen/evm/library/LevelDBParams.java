package com.horizen.evm.library;

public class LevelDBParams extends JsonPointer {
    public String path;

    public LevelDBParams() {
    }

    public LevelDBParams(String path) {
        this.path = path;
    }
}
