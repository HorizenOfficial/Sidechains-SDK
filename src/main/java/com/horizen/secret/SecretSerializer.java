package com.horizen.secret;

import scala.util.Try;
import scorex.core.serialization.Serializer;

public interface SecretSerializer<S extends Secret> extends Serializer<S>
{
    @Override
    byte[] toBytes(S obj);

    @Override
    Try<S> parseBytes(byte[] bytes);
}