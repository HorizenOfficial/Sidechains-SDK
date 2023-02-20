package com.horizen.evm.interop;

import com.horizen.evm.JsonPointer;

public class HandleParams extends JsonPointer {
    public final int handle;

    public HandleParams(int handle) {
        this.handle = handle;
    }
}
