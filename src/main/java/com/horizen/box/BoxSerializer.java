package com.horizen.box;

import scala.util.Try;
import scorex.core.serialization.Serializer;

public interface BoxSerializer<B extends Box> extends Serializer<B>
{
    @Override
    byte[] toBytes(B obj);

    @Override
    Try<B> parseBytes(byte[] bytes);
}