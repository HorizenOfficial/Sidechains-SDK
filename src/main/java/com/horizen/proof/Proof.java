package com.horizen.proof;

import com.horizen.proposition.Proposition;

import scala.util.Try;
import scorex.core.serialization.Serializer;

/*
trait Proof[P <: Proposition] extends BytesSerializable {
  def isValid(proposition: P, message: Array[Byte]): Boolean
}

trait ProofOfKnowledge[S <: Secret, P <: ProofOfKnowledgeProposition[S]] extends Proof[P]
 */

public interface Proof<P extends Proposition> extends scorex.core.transaction.proof.Proof<P>
{
    boolean isValid(P proposition, byte[] message);

    byte[] bytes();

    @Override
    ProofSerializer serializer();
}

