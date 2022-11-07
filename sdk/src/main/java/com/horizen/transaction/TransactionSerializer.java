package com.horizen.transaction;

import sparkz.core.serialization.SparkzSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public interface TransactionSerializer<T extends Transaction>
    extends SparkzSerializer<T>
{
    @Override
    void serialize(T transaction, Writer writer);

    @Override
    T parse(Reader reader);
}

