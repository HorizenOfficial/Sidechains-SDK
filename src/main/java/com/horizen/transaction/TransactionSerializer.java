package com.horizen.transaction;

import scala.util.Try;
import scorex.core.serialization.Serializer;

public interface TransactionSerializer<T extends Transaction> extends Serializer<T>
{
    @Override
    byte[] toBytes(T obj);

    @Override
    Try<T> parseBytes(byte[] bytes);
}

