package com.horizen.proposition;

/*
trait Proposition extends BytesSerializable

trait ProofOfKnowledgeProposition[S <: Secret] extends Proposition
 */

import com.horizen.serialization.JsonSerializable;
import scala.util.Try;
import scorex.core.serialization.ScorexSerializer;

public interface Proposition
    extends scorex.core.transaction.box.proposition.Proposition
    , JsonSerializable
{
    @Override
    byte[] bytes();

    @Override
    PropositionSerializer serializer();
}
