package com.horizen.storage;

import com.horizen.utils.ByteArrayWrapper;
import java.util.List;
import java.util.Optional;


public interface VersionedStorage extends VersionedStorageReader {

    // return the last stored version
    Optional<ByteArrayWrapper> lastVersionID();

    // rollback to the input version
    void rollback(ByteArrayWrapper versionID);

    // return the list of all versions
    List<ByteArrayWrapper> rollbackVersions();

    boolean isEmpty();

    void close();

    // Get a view targeting the current version.
    VersionedStorageView getView();

    // Try to get a view targeting the version in the past if it exists.
    Optional<VersionedStorageView> getView(ByteArrayWrapper version);

    // add a logical partition
    void addLogicalPartition(String name);

    Optional<VersionedStoragePartitionView> getPartitionView(String logicalPartitionName);
    Optional<VersionedStoragePartitionView> getPartitionView(String logicalPartitionName, ByteArrayWrapper version);

}
