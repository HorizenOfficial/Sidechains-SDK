package com.horizen.transaction;

import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public interface TransactionSerializer<T extends Transaction>
    extends ScorexSerializer<T>
{
    @Override
    void serialize(T transaction, Writer writer);

    @Override
    T parse(Reader reader);
}

