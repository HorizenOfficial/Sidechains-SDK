package com.horizen.secret;

import com.horizen.proposition.ProofOfKnowledgeProposition;


public interface Secret extends scorex.core.serialization.BytesSerializable
{
    byte secretTypeId();

    ProofOfKnowledgeProposition publicImage();

    SecretCompanion companion();

    @Override
    byte[] bytes();

    @Override
    SecretSerializer serializer();
}
