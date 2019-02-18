package com.horizen.utils;

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;

public final class BytesUtils {
    private BytesUtils() {}

    // Get Short value from byte array starting from an offset position without copying an array
    public static short getShort(byte[] bytes, int offset) {
        if(offset < 0 || bytes.length < offset + 2)
            throw new IllegalArgumentException("Value is out of array bounds");

        return Shorts.fromBytes(  bytes[offset],
                bytes[offset + 1]);
    }

    // Get Int value from byte array starting from an offset position without copying an array
    public static int getInt(byte[] bytes, int offset) {
        if(offset < 0 || bytes.length < offset + 4)
            throw new IllegalArgumentException("Value is out of array bounds");

        return Ints.fromBytes(  bytes[offset],
                                bytes[offset + 1],
                                bytes[offset + 2],
                                bytes[offset + 3]);
    }

    // Get Long value from byte array starting from an offset position without copying an array
    public static long getLong(byte[] bytes, int offset) {
        if(offset < 0 || bytes.length < offset + 8)
            throw new IllegalArgumentException("Value is out of array bounds");

        return Longs.fromBytes( bytes[offset],
                                bytes[offset + 1],
                                bytes[offset + 2],
                                bytes[offset + 3],
                                bytes[offset + 4],
                                bytes[offset + 5],
                                bytes[offset + 6],
                                bytes[offset + 7]);
    }

    // Get reversed copy of byte array
    public static byte[] reverseBytes(byte[] bytes) {
        byte[] res = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++)
            res[i] = bytes[bytes.length - 1 - i];
        return res;
    }

    // Get byte array from hex string;
    public static byte[] fromHexString(String hex) {
        return BaseEncoding.base16().lowerCase().decode(hex);
    }

    // Get hex string representation of byte array
    public static String toHexString(byte[] bytes) {
        return BaseEncoding.base16().lowerCase().encode(bytes);
    }
}
