package com.horizen.proposition;

import scala.util.Try;
import scorex.core.serialization.ScorexSerializer;

public interface PropositionSerializer<P extends Proposition> extends ScorexSerializer<P>
{
    @Override
    byte[] toBytes(P obj);

    @Override
    Try<P> parseBytesTry(byte[] bytes);
}

