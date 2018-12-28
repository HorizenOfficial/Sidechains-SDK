package com.horizen.secret;

import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proof.ProofOfKnowledge;

import scala.Tuple2;


public interface Secret extends scorex.core.serialization.BytesSerializable
{
    SecretCompanion companion();

    ProofOfKnowledgeProposition<Secret> publicImage();

    @Override
    byte[] bytes();

    @Override
    SecretSerializer serializer();

    // TO DO: uncomment and fix compiler error with access to scala package object in java interface
    byte secretTypeId();
}
