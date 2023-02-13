package com.horizen.evm;

import com.horizen.evm.utils.Converter;
import com.horizen.evm.utils.Hash;

import java.util.Arrays;

public class LibEvmTestBase {
    protected static byte[] bytes(String hex) {
        return Converter.fromHexString(hex);
    }

    protected static byte[] concat(byte[] a, byte[] b) {
        var merged = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, merged, a.length, b.length);
        return merged;
    }

    protected static Hash padToHash(byte[] bytes) {
        var padded = new byte[Hash.LENGTH];
        System.arraycopy(bytes, 0, padded, padded.length - bytes.length, bytes.length);
        return new Hash(padded);
    }
}
