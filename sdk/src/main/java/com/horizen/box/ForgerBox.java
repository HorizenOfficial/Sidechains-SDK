package com.horizen.box;

import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.vrf.VRFPublicKey;

public final class ForgerBox
        extends PublicKey25519NoncedBox<PublicKey25519Proposition>
        implements CoinsBox<PublicKey25519Proposition>
{
    protected VRFPublicKey vrfPubKey;
    protected PublicKey25519Proposition rewardProposition;

    public ForgerBox(VRFPublicKey vrfPubKey, PublicKey25519Proposition rewardProposition, PublicKey25519Proposition proposition,
                     long nonce,
                     long value)
    {
        super(proposition, nonce, value);
        this.vrfPubKey = vrfPubKey;
        this.rewardProposition = rewardProposition;
    }

    @Override
    public byte[] bytes() {
        return new byte[0];
    }

    public static final byte BOX_TYPE_ID = 2;

    @Override
    public BoxSerializer serializer() {
        return null;
    }

    @Override
    public byte boxTypeId() {
        return BOX_TYPE_ID;
    }
}
