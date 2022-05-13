package com.horizen.storage;

import com.horizen.utils.Pair;
import java.util.List;

public interface VersionedStorageWriter extends VersionedStoragePartitionWriter{
    void update(String logicalPartitionName, List<Pair<byte[], byte[]>> toUpdate, List<byte[]> toRemove);
}
