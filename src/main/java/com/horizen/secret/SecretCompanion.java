package com.horizen.secret;

import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proposition.ProofOfKnowledgeProposition;


public interface SecretCompanion<S extends Secret, P extends ProofOfKnowledgeProposition<S>, PR extends ProofOfKnowledge<S,P>>
{
    boolean verify(byte[] message, P publicImage, PR proof);

    // Secret always know about its public key, so no need to return tuple2<privatekey,publickey>
    S generateSecret(byte[] randomSeed);
    // TO DO: change to ...(NodeWallet wallet, byte[] seed);
}