package com.horizen.secret;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.serialization.Views;

@JsonView(Views.Default.class)
public interface Secret
    extends scorex.core.serialization.BytesSerializable
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
