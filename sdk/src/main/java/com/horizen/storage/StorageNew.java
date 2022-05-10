package com.horizen.storage;

import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.Pair;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface StorageNew {

    Optional<byte[]> get(byte[] key);
    byte[] getOrElse(byte[] key, byte[] defaultValue);

    java.util.Map<byte[], Optional<byte[]>> get(Set<byte[]> keys);

    java.util.Map<byte[], byte[]> getAll();

    Optional<ByteArrayWrapper> lastVersionID();

    void update(ByteArrayWrapper version,
                Map<byte[], byte[]> toUpdate, Set<byte[]> toDelete);
    void rollback(ByteArrayWrapper versionID);

    List<ByteArrayWrapper> rollbackVersions();

    boolean isEmpty();

    void close();
}
