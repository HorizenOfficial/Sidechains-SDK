package com.horizen.proposition;

import scala.util.Try;
import scorex.core.serialization.Serializer;

interface PropositionSerializer<P extends Proposition> extends Serializer<P>
{
    @Override
    byte[] toBytes(P obj);

    @Override
    Try<P> parseBytes(byte[] bytes);
}

