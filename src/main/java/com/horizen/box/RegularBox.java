package com.horizen.box;

import com.horizen.proposition.PublicKey25519Proposition;


public final class RegularBox extends PublicKey25519NoncedBox<PublicKey25519Proposition> implements CoinsBox<PublicKey25519Proposition>
{
    public RegularBox(PublicKey25519Proposition proposition,
               long nonce,
               long value)
    {
        super(proposition, nonce, value);
    }

    @Override
    public BoxSerializer serializer() {
        return new RegularBoxSerializer();
    }

    @Override
    public byte boxTypeId() {
        return 0;
    }
}
