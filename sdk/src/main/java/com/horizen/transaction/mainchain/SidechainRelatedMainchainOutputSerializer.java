package com.horizen.transaction.mainchain;

import scala.util.Try;
import sparkz.core.serialization.SparkzSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public interface SidechainRelatedMainchainOutputSerializer<T extends SidechainRelatedMainchainOutput> extends SparkzSerializer<T>
{
    @Override
    public void serialize(T transaction, Writer writer);

    @Override
    public T parse(Reader reader);
}