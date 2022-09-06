package com.horizen.proposition;

import sparkz.core.serialization.SparkzSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public interface PropositionSerializer<P extends Proposition>
    extends SparkzSerializer<P>
{
    @Override
    void serialize(P proposition, Writer writer);

    @Override
    P parse(Reader reader);
}

