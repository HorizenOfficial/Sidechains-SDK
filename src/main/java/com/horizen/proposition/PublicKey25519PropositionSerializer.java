package com.horizen.proposition;

import scala.util.Try;

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
