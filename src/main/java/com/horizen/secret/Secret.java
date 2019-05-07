package com.horizen.secret;

import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proposition.ProofOfKnowledgeProposition;


public interface Secret extends scorex.core.serialization.BytesSerializable
{
    byte secretTypeId();

    ProofOfKnowledgeProposition publicImage();

    @Override
    byte[] bytes();

    @Override
    SecretSerializer serializer();

    boolean owns(ProofOfKnowledgeProposition proposition);

    ProofOfKnowledge sign(byte[] message);
}
