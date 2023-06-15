package io.horizen.examples.transaction;

import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

import java.util.ArrayList;
import java.util.List;

public final class TransactionSerializerUtils {
    public static void putBytesFixedList(List<byte[]> blist, Writer writer) {
        writer.putInt(blist.size());
        blist.forEach(writer::putBytes);
    }

    public static List<byte[]> getBytesFixedList(int elementSize, Reader reader) {
        int elements = reader.getInt();
        List<byte[]> ret = new ArrayList<>();
        while (elements > 0) {
            ret.add(getBytesFixed(elementSize, reader));
            elements--;
        }
        return ret;
    }

    public static byte[] getBytesFixed(int size, Reader reader) {
        return reader.getBytes(size);
    }
}
