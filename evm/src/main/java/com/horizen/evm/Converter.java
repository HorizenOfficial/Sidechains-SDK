package com.horizen.evm;

import com.google.common.io.BaseEncoding;

final class Converter {
    private Converter() {}

    // Get byte array from hex string
    public static byte[] fromHexString(String hex) {
        return BaseEncoding.base16().lowerCase().decode(hex.toLowerCase());
    }

    // Get hex string representation of byte array
    public static String toHexString(byte[] bytes) {
        return BaseEncoding.base16().lowerCase().encode(bytes);
    }
}
