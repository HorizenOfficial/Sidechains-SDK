package com.horizen.box;

import com.horizen.proposition.PublicKey25519Proposition;
import scala.util.Try;

// Example of SDK known Transaction type
public final class RegularBox extends PublicKey25519NoncedBox<PublicKey25519Proposition> implements CoinsBox<PublicKey25519Proposition>
{
    public RegularBox(PublicKey25519Proposition proposition,
               int nonce,
               int value)
    {
        super(proposition, nonce, value);
    }

    @Override
    public RegularBoxSerializer serializer() {
        return new RegularBoxSerializer();
    }

    @Override
    public scorex.core.ModifierTypeId boxTypeId() {
        return null; // scorex.core.ModifierTypeId @@ 3.toByte
    }
}
