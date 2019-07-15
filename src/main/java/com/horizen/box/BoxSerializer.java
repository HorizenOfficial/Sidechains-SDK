package com.horizen.box;

import scala.util.Try;
import scorex.core.serialization.ScorexSerializer;

public interface BoxSerializer<B extends Box> extends ScorexSerializer<B>
{
    @Override
    byte[] toBytes(B obj);

    @Override
    Try<B> parseBytesTry(byte[] bytes);
}