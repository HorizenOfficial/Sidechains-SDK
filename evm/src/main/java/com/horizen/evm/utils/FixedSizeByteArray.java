package com.horizen.evm.utils;

import java.util.Arrays;

public class FixedSizeByteArray {
    private static final String PREFIX = "0x";
    private final int length;
    private final byte[] bytes;

    protected FixedSizeByteArray(int length, byte[] bytes) {
        if (bytes.length != length) {
            throw new IllegalArgumentException(
                String.format("invalid length: want %d bytes got %d", length, bytes.length));
        }
        this.length = length;
        // create a copy to make sure there is no outside reference to these bytes
        this.bytes = Arrays.copyOf(bytes, length);
    }

    protected FixedSizeByteArray(int length, String hex) {
        if (!hex.startsWith(PREFIX)) {
            throw new IllegalArgumentException("hex string must be prefixed with " + PREFIX);
        }
        if (hex.length() != length * 2 + PREFIX.length()) {
            throw new IllegalArgumentException(
                String.format(
                    "invalid length: want %d hex characters got %d", length * 2 + PREFIX.length(), hex.length()));
        }
        this.length = length;
        this.bytes = Converter.fromHexString(hex.substring(2));
    }

    @Override
    public String toString() {
        return PREFIX + Converter.toHexString(bytes);
    }

    public String toStringNoPrefix() {
        return Converter.toHexString(bytes);
    }

    public byte[] toBytes() {
        return Arrays.copyOf(bytes, length);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof FixedSizeByteArray)) return false;
        if (obj == this) return true;
        var other = (FixedSizeByteArray) obj;
        return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
