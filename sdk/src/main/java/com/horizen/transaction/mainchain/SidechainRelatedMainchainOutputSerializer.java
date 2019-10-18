package com.horizen.transaction.mainchain;

import scala.util.Try;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public interface SidechainRelatedMainchainOutputSerializer<T extends SidechainRelatedMainchainOutput> extends ScorexSerializer<T>
{
    @Override
    public void serialize(T transaction, Writer writer);

    @Override
    public T parse(Reader reader);
}