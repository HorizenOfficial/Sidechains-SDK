package com.horizen.secret;

import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proof.ProofOfKnowledge;

import scala.Tuple2;

// TO DO: remove
import scorex.core.transaction.box.Box;


public interface Secret extends scorex.core.transaction.state.Secret
{
    @Override
    SecretCompanion companion();

    @Override
    Secret instance();

    @Override
    ProofOfKnowledgeProposition<Secret> publicImage();

    @Override
    byte[] bytes();

    @Override
    SecretSerializer serializer();

    // TO DO: uncomment and fix compiler error with acces to scla package object in java interface
    byte secretTypeId();
}
