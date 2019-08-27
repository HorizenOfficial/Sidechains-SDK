package com.horizen.transaction;

import com.horizen.serialization.JsonSerializer;
import scala.util.Try;
import scorex.core.serialization.ScorexSerializer;

public interface TransactionSerializer<T extends Transaction>
    extends ScorexSerializer<T>
    , JsonSerializer<T>
{
    //@Override
    //byte[] toBytes(T obj);

    //@Override
    //Try<T> parseBytesTry(byte[] bytes);
}

