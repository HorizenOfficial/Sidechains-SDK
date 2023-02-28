package io.horizen.proof;

import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.secret.Secret;

public interface ProofOfKnowledge<S extends Secret, P extends ProofOfKnowledgeProposition<S>> extends Proof<P>
{

}
