package com.horizen.evm;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Converter;
import com.horizen.evm.utils.Hash;
import scala.Array;

import java.util.Arrays;

public class LibEvmTestBase {
    protected static Address address(String hex) {
        return new Address(hex);
    }

    protected static byte[] bytes(String hex) {
        return Converter.fromHexString(hex);
    }

    protected static String hex(byte[] bytes) {
        return Converter.toHexString(bytes);
    }

    protected static byte[] concat(byte[] a, byte[] b) {
        var merged = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, merged, a.length, b.length);
        return merged;
    }

    protected static byte[] concat(String a, String b) {
        return concat(bytes(a), bytes(b));
    }

    protected static Hash padToHash(byte[] bytes) {
        var padded = new byte[Hash.LENGTH];
        Array.copy(bytes, 0, padded, padded.length - bytes.length, bytes.length);
        return new Hash(padded);
    }
}
