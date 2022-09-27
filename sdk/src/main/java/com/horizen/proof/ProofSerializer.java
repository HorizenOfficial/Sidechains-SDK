package com.horizen.proof;

import sparkz.core.serialization.SparkzSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public interface ProofSerializer<P extends Proof>
    extends SparkzSerializer<P>
{
    @Override
    void serialize(P proof, Writer writer);

    @Override
    P parse(Reader reader);
}
