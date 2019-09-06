package com.horizen.proposition;

/*
trait Proposition extends BytesSerializable

trait ProofOfKnowledgeProposition[S <: Secret] extends Proposition
 */

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.JsonSerializable;
import com.horizen.serialization.Views;
import scala.util.Try;
import scorex.core.serialization.ScorexSerializer;

public interface Proposition extends scorex.core.transaction.box.proposition.Proposition, JsonSerializable
{
    @JsonView(Views.Default.class)
    @JsonProperty("publicKey")
    @Override
    byte[] bytes();

    @Override
    PropositionSerializer serializer();
}
