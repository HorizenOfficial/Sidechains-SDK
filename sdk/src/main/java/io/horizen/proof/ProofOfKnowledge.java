package io.horizen.proof;

import io.horizen.proposition.ProofOfKnowledgeProposition;
import io.horizen.secret.Secret;

public interface ProofOfKnowledge<S extends Secret, P extends ProofOfKnowledgeProposition<S>> extends Proof<P>
{

}
