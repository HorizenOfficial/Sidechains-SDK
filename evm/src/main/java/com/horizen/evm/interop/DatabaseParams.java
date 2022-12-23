package com.horizen.evm.interop;

import com.horizen.evm.JsonPointer;

public class DatabaseParams extends JsonPointer {
    public int databaseHandle;

    public DatabaseParams(int databaseHandle) {
        this.databaseHandle = databaseHandle;
    }
}
