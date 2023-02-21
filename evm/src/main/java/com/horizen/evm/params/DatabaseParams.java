package com.horizen.evm.params;

import com.horizen.evm.JsonPointer;

public class DatabaseParams extends JsonPointer {
    public final int databaseHandle;

    public DatabaseParams(int databaseHandle) {
        this.databaseHandle = databaseHandle;
    }
}
