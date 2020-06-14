package com.horizen.proposition;

import com.horizen.secret.Secret;

/*
trait Proposition extends BytesSerializable

trait ProofOfKnowledgeProposition[S <: Secret] extends Proposition
 */

public interface ProofOfKnowledgeProposition<S extends Secret> extends Proposition
{
    byte[] pubKeyBytes();
}
