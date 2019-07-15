package com.horizen.secret;

import scala.util.Try;
import scorex.core.serialization.ScorexSerializer;

public interface SecretSerializer<S extends Secret> extends ScorexSerializer<S>
{
    @Override
    byte[] toBytes(S obj);

    @Override
    Try<S> parseBytesTry(byte[] bytes);
}