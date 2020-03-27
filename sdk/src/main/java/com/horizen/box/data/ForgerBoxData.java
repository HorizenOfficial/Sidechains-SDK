package com.horizen.box.data;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.ForgerBox;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.vrf.VrfPublicKey;
import scorex.crypto.hash.Blake2b256;

import java.util.Arrays;
import java.util.Objects;

import static com.horizen.box.data.CoreBoxesDataIdsEnum.ForgerBoxDataId;

public final class ForgerBoxData extends AbstractNoncedBoxData<PublicKey25519Proposition, ForgerBox, ForgerBoxData> {
    private final PublicKey25519Proposition rewardProposition;
    private final VrfPublicKey vrfPublicKey;

    public ForgerBoxData(PublicKey25519Proposition proposition,
                         long value,
                         PublicKey25519Proposition rewardProposition,
                         VrfPublicKey vrfPublicKey) {
        super(proposition, value);
        Objects.requireNonNull(rewardProposition, "rewardProposition must be defined");
        Objects.requireNonNull(vrfPublicKey, "vrfPublicKey must be defined");

        this.rewardProposition = rewardProposition;
        this.vrfPublicKey = vrfPublicKey;
    }

    public PublicKey25519Proposition rewardProposition() {
        return rewardProposition;
    }

    public VrfPublicKey vrfPublicKey() {
        return vrfPublicKey;
    }

    @Override
    public ForgerBox getBox(long nonce) {
        return new ForgerBox(this, nonce);
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(
                proposition().bytes(),
                Longs.toByteArray(value()),
                rewardProposition().bytes(),
                vrfPublicKey().bytes()
        );
    }

    @Override
    public NoncedBoxDataSerializer serializer() {
        return ForgerBoxDataSerializer.getSerializer();
    }

    @Override
    public byte boxDataTypeId() {
        return ForgerBoxDataId.id();
    }

    public static ForgerBoxData parseBytes(byte[] bytes) {
        int valueOffset = PublicKey25519Proposition.getLength();
        int rewardPropositionOffset = valueOffset + Longs.BYTES;
        int vrfPubKeyOffset = rewardPropositionOffset + PublicKey25519Proposition.getLength();

        PublicKey25519Proposition proposition = PublicKey25519PropositionSerializer.getSerializer().parseBytes(Arrays.copyOf(bytes, valueOffset));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, valueOffset, rewardPropositionOffset));
        PublicKey25519Proposition rewardProposition = PublicKey25519Proposition.parseBytes(Arrays.copyOfRange(bytes, rewardPropositionOffset, vrfPubKeyOffset));
        VrfPublicKey vrfPublicKey = VrfPublicKey.parseBytes(Arrays.copyOfRange(bytes, vrfPubKeyOffset, bytes.length));

        return new ForgerBoxData(proposition, value, rewardProposition, vrfPublicKey);
    }

    @Override
    public byte[] customFieldsHash() {
        return Blake2b256.hash(Bytes.concat(rewardProposition().pubKeyBytes(), vrfPublicKey().pubKeyBytes()));
    }
}
