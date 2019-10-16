package com.horizen.proof;

import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public interface ProofSerializer<P extends Proof>
    extends ScorexSerializer<P>
{
    @Override
    void serialize(P proof, Writer writer);

    @Override
    P parse(Reader reader);
}
