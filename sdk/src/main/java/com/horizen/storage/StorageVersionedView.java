package com.horizen.storage;

import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.Pair;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface StorageVersionedView extends VersionedReader {

    Optional<String> getVersion();

    void update(List<Pair<byte[], byte[]>> toUpdate, List<byte[]> toRemove);

    void commit(ByteArrayWrapper version);


}
