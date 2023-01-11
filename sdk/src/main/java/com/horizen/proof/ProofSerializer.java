package com.horizen.proof;

import sparkz.core.serialization.SparkzSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public interface ProofSerializer<P extends Proof>
    extends SparkzSerializer<P>
{
    @Override
    void serialize(P proof, Writer writer);

    @Override
    P parse(Reader reader);
}
