package com.horizen.proposition;

/*
trait Proposition extends BytesSerializable

trait ProofOfKnowledgeProposition[S <: Secret] extends Proposition
 */

import scala.util.Try;
import scorex.core.serialization.Serializer;

public interface Proposition extends scorex.core.transaction.box.proposition.Proposition
{
    @Override
    byte[] bytes();

    @Override
    PropositionSerializer serializer();
}
