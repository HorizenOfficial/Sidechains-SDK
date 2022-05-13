package com.horizen.storage;

import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.Pair;
import java.util.List;

public interface VersionedStoragePartitionView extends VersionedStoragePartitionReader {
    void update(List<Pair<byte[], byte[]>> toUpdate, List<byte[]> toRemove);
    void commit(ByteArrayWrapper version);
}
