package com.horizen.box;

import com.horizen.serialization.JsonSerializer;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public interface BoxSerializer<B extends Box>
    extends ScorexSerializer<B>
    , JsonSerializer<B>
{
    @Override
    void serialize(B box, Writer writer);

    @Override
    B parse(Reader reader);
}