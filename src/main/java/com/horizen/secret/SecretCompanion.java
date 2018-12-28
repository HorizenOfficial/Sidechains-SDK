package com.horizen.secret;

import com.horizen.box.Box;
import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.Proposition;
import scala.Tuple2;


/**
 * TO DO: scorex.core.transaction.state.SecretCompanion must provide PK, PR and Box as a polymorphic objects of the class
 * to be Java friendly and to allow us to override this class methods for nested Objects.
 */

interface SecretCompanion<S extends Secret, P extends ProofOfKnowledgeProposition<S>, PR extends ProofOfKnowledge<S,P>, B extends Box<P>>
{
    boolean owns(S secret, B box);

    // TO DO: check ProofOfKnowledge usage
    PR sign(S secret, byte[] message);

    boolean verify(byte[] message, P publicImage, PR proof);

    Tuple2<S, P> generateKeys(byte[] randomSeed);
}

