package com.horizen.storage;

import java.util.Optional;
import java.util.List;
import com.horizen.utils.Pair;
import com.horizen.utils.ByteArrayWrapper;

public interface Storage {

    Optional<ByteArrayWrapper> get(ByteArrayWrapper key);
    ByteArrayWrapper getOrElse(ByteArrayWrapper key, ByteArrayWrapper defaultValue);
    List<Pair<ByteArrayWrapper,Optional<ByteArrayWrapper>>> get(List<ByteArrayWrapper> keys);
    List<Pair<ByteArrayWrapper,ByteArrayWrapper>> getAll();

    Optional<ByteArrayWrapper> lastVersionID();

    void update(ByteArrayWrapper version, List<Pair<ByteArrayWrapper, ByteArrayWrapper>> toUpdate,
                List<ByteArrayWrapper> toRemove);

    void rollback(ByteArrayWrapper versionID);

    List<ByteArrayWrapper> rollbackVersions();

    boolean isEmpty();

    void close();
}
