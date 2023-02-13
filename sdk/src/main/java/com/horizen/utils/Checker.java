package com.horizen.utils;

import sparkz.util.serialization.Reader;
public class Checker {
    public static byte[] readBytes(Reader reader, int needed, String type) {
        int remaining = reader.remaining();
        if (needed > remaining) {
            throw new IllegalArgumentException(String.format("Bytes remaining in buffer %d are not enough " +
                    "to parse %s of length %d.", remaining, type, needed));
        }
        return reader.getBytes(needed);
    }

    public static byte readByte(Reader reader,  String type) {
        int remaining = reader.remaining();
        if (remaining < 1) {
            throw new IllegalArgumentException(String.format("Bytes remaining in buffer %d are not enough " +
                    "to parse one bite for %s", remaining, type));
        }
        return reader.getByte();
    }

    public static long readLongNotLessThanZero(Reader reader, String type) {
        long value = reader.getLong();
        if (value < 0) {
            throw new IllegalArgumentException(String.format("Length %d of %s should be not less than zero.", value, type));
        }
        return value;
    }

    public static int readIntNotLessThanZero(Reader reader, String type) {
        int value = reader.getInt();
        if (value < 0) {
            throw new IllegalArgumentException(String.format("Length %d of %s should be not less than zero.", value, type));
        }
        return value;
    }

    public static long readLongGreaterThanZero(Reader reader, String type) {
        long value = reader.getLong();
        if (value <= 0) {
            throw new IllegalArgumentException(String.format("Length %d of %s should be greater than zero.", value, type));
        }
        return value;
    }

    public static int readIntGreaterThanZero(Reader reader, String type) {
        int value = reader.getInt();
        if (value <= 0) {
            throw new IllegalArgumentException(String.format("Length %d of %s should be greater than zero.", value, type));
        }
        return value;
    }

    public static int equalZeroOrOne(int value, String type) {
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException(String.format("%s should be 0 or 1 instead of %d", type, value));
        }
        return value;
    }

    public static void bufferShouldBeEmpty(int remaining) {
        if (remaining != 0) {
            throw new IllegalArgumentException("There's more data in the buffer than required");
        }
    }

    public static byte version(Reader reader, byte expected, String type) {
        byte actual = readByte(reader, type);
        if (actual != expected) {
            throw new IllegalArgumentException(String.format("%s version in unsupported, expected=%d, actual=%d",
                    type, expected, actual));
        }
        return actual;
    }
}
