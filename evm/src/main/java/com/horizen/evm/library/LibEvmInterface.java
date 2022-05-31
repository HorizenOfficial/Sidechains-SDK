package com.horizen.evm.library;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

public interface LibEvmInterface extends Library {
    void Free(Pointer ptr);

    JsonString Invoke(String method, JsonPointer args);
}
