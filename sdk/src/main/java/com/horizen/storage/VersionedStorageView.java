package com.horizen.storage;

import com.horizen.utils.ByteArrayWrapper;
import java.util.Optional;

public interface VersionedStorageView extends VersionedStorageReader, VersionedStorageWriter {

    Optional<String> getVersion();

    void commit(ByteArrayWrapper version);

    Optional<VersionedStoragePartitionView> getPartitionView(String logicalPartitionName);

}
