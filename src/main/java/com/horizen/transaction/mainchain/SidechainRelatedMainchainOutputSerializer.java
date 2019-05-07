package com.horizen.transaction.mainchain;

import scala.util.Try;
import scorex.core.serialization.Serializer;

public interface SidechainRelatedMainchainOutputSerializer<T extends SidechainRelatedMainchainOutput> extends Serializer<T>
{
    @Override
    byte[] toBytes(T obj);

    @Override
    Try<T> parseBytes(byte[] bytes);
}