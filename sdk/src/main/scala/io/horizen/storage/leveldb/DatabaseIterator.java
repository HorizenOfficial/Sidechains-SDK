package io.horizen.storage.leveldb;

import io.horizen.storage.StorageIterator;
import org.iq80.leveldb.DBIterator;

import java.io.IOException;
import java.util.Map;

public class DatabaseIterator implements StorageIterator {
    DBIterator iterator;

    DatabaseIterator(DBIterator iterator) {
        this.iterator = iterator;
    }

    @Override
    public void seek(byte[] key) {
        iterator.seek(key);
    }

    @Override
    public void seekToFirst() {
        iterator.seekToFirst();
    }

    @Override
    public void close() throws IOException {
        iterator.close();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public Map.Entry<byte[], byte[]> next() {
        return iterator.next();
    }
}
