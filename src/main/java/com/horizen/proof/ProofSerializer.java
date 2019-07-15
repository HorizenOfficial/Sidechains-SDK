package com.horizen.proof;

import scala.util.Try;
import scorex.core.serialization.ScorexSerializer;

public interface ProofSerializer<P extends Proof> extends ScorexSerializer<P>
{
    @Override
    byte[] toBytes(P obj);

    @Override
    Try<P> parseBytesTry(byte[] bytes);
}