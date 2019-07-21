package com.horizen.proposition;

import scala.util.Try;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public interface PropositionSerializer<P extends Proposition> extends ScorexSerializer<P>
{
    @Override
    void serialize(P proposition, Writer writer);

    @Override
    P parse(Reader reader);
}

