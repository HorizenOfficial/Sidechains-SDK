package com.horizen.evm;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Converter;

import java.util.Arrays;

public class LibEvmTestBase {
    static final byte[] hashNull = bytes("0000000000000000000000000000000000000000000000000000000000000000");
    static final byte[] hashEmpty = bytes("56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");

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
}
