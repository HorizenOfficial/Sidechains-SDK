package com.horizen.storage;

import com.horizen.utils.Pair;
import java.util.List;

public interface VersionedStoragePartitionWriter {
    void update(List<Pair<byte[], byte[]>> toUpdate, List<byte[]> toRemove);
}
