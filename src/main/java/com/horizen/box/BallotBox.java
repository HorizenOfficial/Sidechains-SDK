package com.horizen.box;

import com.horizen.proposition.PublicKey25519Proposition;

public class BallotBox extends PublicKey25519NoncedBox<PublicKey25519Proposition>
{
    BallotBox(PublicKey25519Proposition proposition,
              int nonce)
    {
        super(proposition, nonce, 0);
    }

    @Override
    public scorex.core.ModifierTypeId boxTypeId() {
        return null; // // scorex.core.ModifierTypeId @@ 1.toByte
    }
}
