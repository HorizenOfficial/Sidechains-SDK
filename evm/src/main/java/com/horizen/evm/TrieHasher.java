package com.horizen.evm;

import com.horizen.evm.params.HashParams;

public final class TrieHasher {
    private TrieHasher() {}

    public static Hash Root(byte[][] values) {
        return LibEvm.invoke("HashRoot", new HashParams(values), Hash.class);
    }
}
