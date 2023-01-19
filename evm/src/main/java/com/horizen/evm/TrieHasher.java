package com.horizen.evm;

public final class TrieHasher {
    private TrieHasher() {}

    public static byte[] Root(byte[][] values) {
        return LibEvm.hashRoot(values);
    }
}
