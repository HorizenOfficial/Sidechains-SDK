package io.horizen.proposition;

import sparkz.core.serialization.SparkzSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public interface PropositionSerializer<P extends Proposition>
    extends SparkzSerializer<P>
{
    @Override
    void serialize(P proposition, Writer writer);

    @Override
    P parse(Reader reader);
}

