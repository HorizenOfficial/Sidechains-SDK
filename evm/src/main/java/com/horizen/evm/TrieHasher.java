package com.horizen.evm;

import com.horizen.evm.interop.HashParams;
import com.horizen.evm.utils.Hash;

public final class TrieHasher {
    private TrieHasher() {}

    public static Hash Root(byte[][] values) {
        return LibEvm.invoke("HashRoot", new HashParams(values), Hash.class);
    }
}
