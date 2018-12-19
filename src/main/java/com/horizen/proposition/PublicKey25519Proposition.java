package com.horizen.proposition;

import scala.util.Try;

import com.horizen.secret.PrivateKey25519;
import com.horizen.ScorexEncodingImpl;

// To Do: replace with proper PublicKey from Scorex
import java.security.PublicKey;

// TO DO: check usage of ScorexEncodingImpl and Scorex core Encoders
public class PublicKey25519Proposition<PK extends PrivateKey25519> extends ScorexEncodingImpl implements ProofOfKnowledgeProposition<PK>
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

class PublicKey25519PropositionSerializer<PKP extends PublicKey25519Proposition> implements PropositionSerializer<PKP>
{

    @Override
    public byte[] toBytes(PKP obj) {
        return new byte[0];
    }

    @Override
    public Try<PKP> parseBytes(byte[] bytes) {
        return null;
    }
}

