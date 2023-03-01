package io.horizen.utils;

import java.util.Comparator;

// Wraps byte array without copying it and provides proper hashCode and compare methods implementation.
public class ByteArrayWrapper implements java.io.Serializable, Comparable<ByteArrayWrapper> {

    private byte[] data;

    public int size() { return data.length; }
    public byte[] data() { return data; }

    public ByteArrayWrapper(byte[] data) {
        assert data != null;
        this.data = data;
    }

    public ByteArrayWrapper(Integer size) {
        this.data = new byte[size];
    }

    public ByteArrayWrapper(ByteArrayWrapper wrapper) {
        this(wrapper.data);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + (
                size() == 8 ? String.valueOf(BytesUtils.getLong(data, 0)) + "L" : BytesUtils.toHexString(data)) + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (!(this.getClass().equals(obj.getClass())))
            return false;
        return java.util.Arrays.equals(data, ((ByteArrayWrapper)obj).data);
    }

    @Override
    public int hashCode() {
        //do not use Arrays.hashCode, it generates too many collisions (31 is too low)
        int h = 1;
        for (byte b : data) {
            h = h * (-1640531527) + b;
        }
        return h;
    }

    @Override
    public int compareTo(ByteArrayWrapper o) {
         return compare(this.data, o.data);
    }

    public static int compare(byte[] o1, byte[] o2) {
//            if (o1 == o2) return 0;
        final int len = Math.min(o1.length, o2.length);
        for (int i = 0; i < len; i++) {
            int b1 = o1[i] & 0xFF;
            int b2 = o2[i] & 0xFF;
            if (b1 != b2)
                return b1 - b2;
        }
        return o1.length - o2.length;
    }
}


