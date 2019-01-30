package com.horizen.utils;

import java.util.Arrays;

// Wraps byte array without copying it and provides proper hashCode and compare methods implementation.
public class ByteArrayWrapper {
    private byte[] _dataRef;

    public ByteArrayWrapper(byte[] data) {
        if(data == null)
            throw new IllegalArgumentException("Parameter can't be null.");
        _dataRef = data;
    }

    public byte[] data() {
        return Arrays.copyOf(_dataRef, _dataRef.length);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_dataRef);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(obj instanceof ByteArrayWrapper))
            return false;
        if (obj == this)
            return true;
        return Arrays.equals(_dataRef, ((ByteArrayWrapper) obj)._dataRef);
    }
}
