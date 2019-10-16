package com.horizen.box;

import com.horizen.proposition.PublicKey25519Proposition;

// EXAMPLE of non-coin box
public abstract class BallotBox extends PublicKey25519NoncedBox<PublicKey25519Proposition>
{
    BallotBox(PublicKey25519Proposition proposition,
              long nonce)
    {
        super(proposition, nonce, 0);
    }

    @Override
    public byte boxTypeId() {
        return 1;
    }
}
