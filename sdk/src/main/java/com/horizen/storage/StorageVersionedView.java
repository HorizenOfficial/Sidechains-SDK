package com.horizen.storage;

import com.horizen.utils.ByteArrayWrapper;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface StorageVersionedView extends VersionedReader {

    Optional<String> getVersion();

    void update(Map<byte[], byte[]> toUpdate, Set<byte[]> toDelete);

    void commit(ByteArrayWrapper version);


}
