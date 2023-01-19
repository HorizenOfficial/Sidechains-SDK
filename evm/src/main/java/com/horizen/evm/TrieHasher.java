package com.horizen.evm;

import com.horizen.evm.interop.HashParams;
import com.horizen.evm.utils.Hash;

public final class TrieHasher {
    private TrieHasher() {}

    public static byte[] Root(byte[][] values) {
        return LibEvm.invoke("HashRoot", new HashParams(values), Hash.class).toBytes();
    }
}
