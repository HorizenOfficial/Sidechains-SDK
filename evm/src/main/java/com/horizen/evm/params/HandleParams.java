package com.horizen.evm.params;

import com.horizen.evm.JsonPointer;

public class HandleParams extends JsonPointer {
    public final int handle;

    public HandleParams(int handle) {
        this.handle = handle;
    }
}
