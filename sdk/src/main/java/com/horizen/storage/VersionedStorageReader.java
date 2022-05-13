package com.horizen.storage;

import com.horizen.utils.Pair;

import java.util.List;

public interface VersionedStorageReader extends VersionedStoragePartitionReader {
    // returns an empty array if there is no such key
    byte[] get(String partitionName, byte[] key);

    // try to get the value of the input key and return the input defaultvalue if could not find it
    byte[] getOrElse(String partitionName, byte[] key, byte[] defaultValue);

    // returns as many value as input keys. An empty byte[] if no such key.
    List<byte[]> get(String partitionName, List<byte[]> keys);

    // return all the contents of the storage (huge return value)
    List<Pair<byte[], byte[]>> getAll(String partitionName);
}
