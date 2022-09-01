package com.horizen.box;

import sparkz.core.serialization.SparkzSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public interface BoxSerializer<B extends Box>
    extends SparkzSerializer<B>
{
    @Override
    void serialize(B box, Writer writer);

    @Override
    B parse(Reader reader);
}