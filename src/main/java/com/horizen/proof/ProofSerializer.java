package com.horizen.proof;

import scala.util.Try;
import scorex.core.serialization.Serializer;

interface ProofSerializer<P extends Proof> extends Serializer<P>
{
    @Override
    byte[] toBytes(P obj);

    @Override
    Try<P> parseBytes(byte[] bytes);
}