package com.horizen.proposition;
import com.horizen.secret.Secret;

import java.util.List;

public interface SingleSecretProofOfKnowledgeProposition<S extends Secret>
      extends ProofOfKnowledgeProposition<S>{

    @Override
    default ProvableCheckResult<S> canBeProvedBy(List<Secret> secretList) {
        for (Secret s : secretList){
            if (s.publicImage().equals(this)){
                S secret = (S) s;
                return new ProvableCheckResultImpl<>(true, secret);
            }
        }
        return new ProvableCheckResultImpl<>(false);
    }
}
