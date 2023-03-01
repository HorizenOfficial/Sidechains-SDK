package io.horizen.storage;

import java.io.Closeable;
import java.util.Iterator;
import java.util.Map;

public interface StorageIterator
        extends Iterator<Map.Entry<byte[], byte[]>>, Closeable
{
    /**
     * Repositions the iterator so the key of the next BlockElement
     * returned greater than or equal to the specified targetKey.
     */
    void seek(byte[] key);

    /**
     * Repositions the iterator so is is at the beginning of the Database.
     */
    void seekToFirst();

}