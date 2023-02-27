package com.horizen.utxo.box.data;

import sparkz.core.serialization.SparkzSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public interface BoxDataSerializer<D extends BoxData>
        extends SparkzSerializer<D>
{
    @Override
    void serialize(D box, Writer writer);

    @Override
    D parse(Reader reader);
}