package com.horizen.storage;

import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.Pair;

import java.util.List;
import java.util.Optional;

public interface VersionedStorageView extends VersionedStorageReader {

    Optional<String> getVersion();

    void update(List<Pair<byte[], byte[]>> toUpdate, List<byte[]> toRemove);

    void update(String logicalPartitionName, List<Pair<byte[], byte[]>> toUpdate, List<byte[]> toRemove);

    void commit(ByteArrayWrapper version);

    VersionedStoragePartitionView getPartitionView(String logicalPartitionName);

}
