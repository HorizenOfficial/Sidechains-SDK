package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.vrf.VRFPublicKey;

import java.util.Arrays;
import java.util.Objects;

public final class ForgerBox
        extends PublicKey25519NoncedBox<PublicKey25519Proposition>
        implements CoinsBox<PublicKey25519Proposition>
{
    public static final byte BOX_TYPE_ID = 2;

    private final PublicKey25519Proposition rewardProposition;
    private final VRFPublicKey vrfPubKey;

    public ForgerBox(PublicKey25519Proposition proposition, long nonce, long value,
                     PublicKey25519Proposition rewardProposition, VRFPublicKey vrfPubKey)
    {
        super(new PublicKey25519Proposition(proposition.pubKeyBytes()), nonce, value);

        Objects.requireNonNull(rewardProposition, "rewardProposition shall be defined");
        Objects.requireNonNull(vrfPubKey, "vrfPubKey shall be defined");

        this.rewardProposition = new PublicKey25519Proposition(rewardProposition.pubKeyBytes());
        this.vrfPubKey = new VRFPublicKey(vrfPubKey.bytes());
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(_proposition.bytes(), Longs.toByteArray(_nonce), Longs.toByteArray(_value),
                rewardProposition.bytes(), vrfPubKey.bytes());
    }

    @Override
    public BoxSerializer serializer() {
        return ForgerBoxSerializer.getSerializer();
    }

    @Override
    public byte boxTypeId() {
        return BOX_TYPE_ID;
    }

    public VRFPublicKey vrfPubKey() {
        return vrfPubKey;
    }

    public PublicKey25519Proposition rewardProposition() {
        return rewardProposition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ForgerBox forgerBox = (ForgerBox) o;
        return vrfPubKey.equals(forgerBox.vrfPubKey) &&
                rewardProposition.equals(forgerBox.rewardProposition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), vrfPubKey, rewardProposition);
    }

    public static int length() {
        int nonceOffset = PublicKey25519Proposition.getLength();
        int valueOffset = nonceOffset + Longs.BYTES;
        int rewardPropositionOffset = valueOffset + Longs.BYTES;
        int vrfPubKeyOffset = rewardPropositionOffset + PublicKey25519Proposition.getLength();
        int totalOffset = vrfPubKeyOffset + VRFPublicKey.KEY_LENGTH;

        return totalOffset;
    }

    public static ForgerBox parseBytes(byte[] bytes) {
        int nonceOffset = PublicKey25519Proposition.getLength();
        int valueOffset = nonceOffset + Longs.BYTES;
        int rewardPropositionOffset = valueOffset + Longs.BYTES;
        int vrfPubKeyOffset = rewardPropositionOffset + PublicKey25519Proposition.getLength();

        PublicKey25519Proposition proposition = PublicKey25519Proposition.parseBytes(Arrays.copyOf(bytes, PublicKey25519Proposition.getLength()));
        long nonce = Longs.fromByteArray(Arrays.copyOfRange(bytes, nonceOffset, valueOffset));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, valueOffset, rewardPropositionOffset));
        PublicKey25519Proposition rewardProposition = PublicKey25519Proposition.parseBytes(Arrays.copyOfRange(bytes, rewardPropositionOffset, vrfPubKeyOffset));
        VRFPublicKey vrfPublicKey = VRFPublicKey.parseBytes(Arrays.copyOfRange(bytes, vrfPubKeyOffset, bytes.length));

        return new ForgerBox(proposition, nonce, value, rewardProposition, vrfPublicKey);
    }
}
