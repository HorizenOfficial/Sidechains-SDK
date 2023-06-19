package io.horizen.transaction.mainchain;

import sparkz.core.serialization.SparkzSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public interface SidechainRelatedMainchainOutputSerializer<T extends SidechainRelatedMainchainOutput> extends SparkzSerializer<T>
{
    @Override
    public void serialize(T transaction, Writer writer);

    @Override
    public T parse(Reader reader);
}