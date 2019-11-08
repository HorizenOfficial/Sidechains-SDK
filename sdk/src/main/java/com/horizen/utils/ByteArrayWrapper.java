package com.horizen.utils;

// Wraps byte array without copying it and provides proper hashCode and compare methods implementation.
public class ByteArrayWrapper extends io.iohk.iodb.ByteArrayWrapper {
    public ByteArrayWrapper(byte[] data) {
        super(data);
    }

    public ByteArrayWrapper(Integer size) {
        super(size);
    }

    public ByteArrayWrapper(io.iohk.iodb.ByteArrayWrapper wrapper) {
        this(wrapper.data());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + (
                size() == 8 ? String.valueOf(getLong(data(), 0)) + "L" : BytesUtils.toHexString(data())) + "]";
    }

    private long getLong(byte[] buf, int pos) {
        return
                ((((long) buf[pos++]) << 56) |
                        (((long) buf[pos++] & 0xFF) << 48) |
                        (((long) buf[pos++] & 0xFF) << 40) |
                        (((long) buf[pos++] & 0xFF) << 32) |
                        (((long) buf[pos++] & 0xFF) << 24) |
                        (((long) buf[pos++] & 0xFF) << 16) |
                        (((long) buf[pos++] & 0xFF) << 8) |
                        (((long) buf[pos] & 0xFF)));

    }
}
