package com.horizen.secret;

import sparkz.core.serialization.SparkzSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public interface SecretSerializer<S extends Secret>
    extends SparkzSerializer<S>
{
    @Override
    void serialize(S secret, Writer writer);

    @Override
    S parse(Reader reader);
}