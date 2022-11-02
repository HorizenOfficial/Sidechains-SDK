package com.horizen.box.data;

import sparkz.core.serialization.SparkzSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public interface BoxDataSerializer<D extends BoxData>
        extends SparkzSerializer<D>
{
    @Override
    void serialize(D box, Writer writer);

    @Override
    D parse(Reader reader);
}