package com.horizen.proposition;

import scala.util.Try;

import com.horizen.secret.PrivateKey25519;
import com.horizen.ScorexEncoding;

// To Do: replace with proper PublicKey from Scorex
import java.security.PublicKey;

// TO DO: check usage of ScorexEncodingImpl and Scorex core Encoders
public class PublicKey25519Proposition<PK extends PrivateKey25519> extends ScorexEncoding implements ProofOfKnowledgeProposition<PK>
{
    // to do: change to scorex.crypto.PublicKey
    PublicKey _pubKeyBytes;

    public PublicKey25519Proposition(PublicKey pubKeyBytes)
    {
        // require check
        _pubKeyBytes = pubKeyBytes;
    }

    public PublicKey pubKeyBytes() {
        return _pubKeyBytes;
    }

    @Override
    public byte[] bytes() {
        return new byte[0];
    }

    @Override
    public PublicKey25519PropositionSerializer serializer() {
        return new PublicKey25519PropositionSerializer();
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return false;
    }
}

