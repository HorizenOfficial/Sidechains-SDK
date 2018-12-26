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
    public byte boxTypeId() {
        return 1; // // scorex.core.ModifierTypeId @@ 1.toByte
    }
}
