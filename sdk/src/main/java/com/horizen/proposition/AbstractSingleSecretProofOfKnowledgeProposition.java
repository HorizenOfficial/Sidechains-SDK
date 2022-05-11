package com.horizen.proposition;
import com.horizen.secret.Secret;

import java.util.List;

public abstract class AbstractSingleSecretProofOfKnowledgeProposition<S extends Secret>
      implements ProofOfKnowledgeProposition<S>{

    @Override
    public ProvableCheckResult<S> canBeProvedBy(List<Secret> secrectList) {
        for (Secret s : secrectList){
            if (s.publicImage().equals(this)){
                return new ProvableCheckResultImpl(true, s);
            }
        }
        return new ProvableCheckResultImpl(false);
    }
}
