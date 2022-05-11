package com.horizen.storage;

import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.Pair;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


public interface StorageNew extends VersionedReader {

    // try to get the value of the input key and return the input defaultvalue if could not find it
    byte[] getOrElse(byte[] key, byte[] defaultValue);

    // return all the contents of the storage (huge return value)
    List<Pair<byte[], byte[]>> getAll();

    // return the last stored version
    Optional<ByteArrayWrapper> lastVersionID();

    // rollback to the input version
    void rollback(ByteArrayWrapper versionID);

    // return the list of all versions
    List<ByteArrayWrapper> rollbackVersions();

    boolean isEmpty();

    void close();

    // Get a view targeting the current version.
    StorageVersionedView getView();

    // Try to get a view targeting the version in the past if it exists.
    Optional<StorageVersionedView> getView(ByteArrayWrapper version);
}
