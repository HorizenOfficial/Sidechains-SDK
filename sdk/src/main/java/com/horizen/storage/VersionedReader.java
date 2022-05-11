package com.horizen.storage;

import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.Pair;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface VersionedReader {
    // returns an empty array if there is no such key
    byte[] get(byte[] key);

    // returns as many value as input keys. An empty byte[] if no such key.
    List<byte[]> get(List<byte[]> keys);
}
