package com.horizen.utils;

// Representation of Bitcoin VarInt, which has different size depening on value: from 1 to 9 bytes.
public class VarInt {
    protected long _value;
    protected int _size;

    public VarInt(long value, int size) {
        _value = value;
        _size = size;
    }

    public long value() {
        return _value;
    }

    public int size() {
        return _size;
    }

    public static int getVarIntSize(long value) {
        int res;
        if(value >> 32 != 0)
            return 9;
        if(value >> 16 != 0)
            return 5;
        if(value >> 8 != 0)
            return 3;
        return 1;
    }
}
