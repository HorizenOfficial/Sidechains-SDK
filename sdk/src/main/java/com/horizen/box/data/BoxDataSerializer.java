package com.horizen.box.data;

import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public interface BoxDataSerializer<D extends BoxData>
        extends ScorexSerializer<D>
{
    @Override
    void serialize(D box, Writer writer);

    @Override
    D parse(Reader reader);
}