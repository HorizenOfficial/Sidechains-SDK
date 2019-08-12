package com.horizen.transaction.mainchain;

import scala.util.Try;
import scorex.core.serialization.ScorexSerializer;

public interface SidechainRelatedMainchainOutputSerializer<T extends SidechainRelatedMainchainOutput> extends ScorexSerializer<T>
{
    //@Override
    //byte[] toBytes(T obj);

    //@Override
    //Try<T> parseBytesTry(byte[] bytes);
}