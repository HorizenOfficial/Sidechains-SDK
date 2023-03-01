package io.horizen.utils;

// Representation of Bitcoin CompactSize, which has different actual size depending on value: from 1 to 9 bytes.
public class CompactSize {
    /**
     * The maximum size of a serialized object in bytes or number of elements
     * (for eg vectors) when the size is encoded as CompactSize.
     */
    public static long MAX_SERIALIZED_COMPACT_SIZE = 0x02000000L;

    protected long _value;
    protected int _size;

    public CompactSize(long value, int size) {
        _value = value;
        _size = size;
    }

    public long value() {
        return _value;
    }

    public int size() {
        return _size;
    }

    public static int getSize(long value) {
        if(value >> 32 != 0)
            return 9;
        if(value >> 16 != 0)
            return 5;
        if(value >> 8 != 0)
            return 3;
        return 1;
    }
}
