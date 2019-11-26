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
                size() == 8 ? String.valueOf(BytesUtils.getLong(data(), 0)) + "L" : BytesUtils.toHexString(data())) + "]";
    }

}
