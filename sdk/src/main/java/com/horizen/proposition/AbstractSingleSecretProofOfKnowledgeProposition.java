package com.horizen.proposition;
import com.horizen.secret.Secret;

import java.util.List;

public interface AbstractSingleSecretProofOfKnowledgeProposition<S extends Secret>
      extends ProofOfKnowledgeProposition<S>{

    @Override
    public default ProvableCheckResult<S> canBeProvedBy(List<Secret> secrectList) {
        for (Secret s : secrectList){
            if (s.publicImage().equals(this)){
                return new ProvableCheckResultImpl(true, s);
            }
        }
        return new ProvableCheckResultImpl(false);
    }
}
