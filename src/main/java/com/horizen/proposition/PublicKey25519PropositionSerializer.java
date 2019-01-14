package com.horizen.proposition;

import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

public class PublicKey25519PropositionSerializer implements PropositionSerializer<PublicKey25519Proposition>
{

    @Override
    public byte[] toBytes(PublicKey25519Proposition obj) {
        return obj.pubKeyBytes();
    }

    @Override
    public Try<PublicKey25519Proposition> parseBytes(byte[] bytes) {
        try {
            PublicKey25519Proposition proposition = new PublicKey25519Proposition(bytes);
            return new Success<PublicKey25519Proposition>(proposition);
        }
        catch (Exception e) {
            return new Failure(e);
        }
    }
}
