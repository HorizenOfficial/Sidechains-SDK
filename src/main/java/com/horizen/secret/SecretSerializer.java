package com.horizen.secret;

import scala.util.Try;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public interface SecretSerializer<S extends Secret> extends ScorexSerializer<S>
{
    @Override
    void serialize(S secret, Writer writer);

    @Override
    S parse(Reader reader);
}