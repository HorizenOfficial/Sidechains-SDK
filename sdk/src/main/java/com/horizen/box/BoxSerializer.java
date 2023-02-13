package com.horizen.box;

import sparkz.core.serialization.SparkzSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public interface BoxSerializer<B extends Box>
    extends SparkzSerializer<B>
{
    @Override
    void serialize(B box, Writer writer);

    @Override
    B parse(Reader reader);
}