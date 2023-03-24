package io.horizen.proposition;

import io.horizen.secret.Secret;

import java.util.List;

/*
trait Proposition extends BytesSerializable

trait ProofOfKnowledgeProposition[S <: Secret] extends Proposition
 */

public interface ProofOfKnowledgeProposition<S extends Secret> extends Proposition
{
    byte[] pubKeyBytes();

    /**
     * Checks if it is possible to build a Proof for this Proposition, giving the list of available Secrets
     * @return an instance of ProvableCheckResult. It contains a boolean flag with the result,
     * and the subset of Secrets that can be used to build the proof
     */
    ProvableCheckResult<S> canBeProvedBy(List<Secret> secrectList);
}
