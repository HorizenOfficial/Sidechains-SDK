package com.horizen.box.data;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.ForgerBox;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.proposition.VrfPublicKey;
import com.horizen.proposition.VrfPublicKeySerializer;
import scorex.crypto.hash.Blake2b256;

import java.util.Arrays;
import java.util.Objects;

import static com.horizen.box.data.CoreBoxesDataIdsEnum.ForgerBoxDataId;

public final class ForgerBoxData extends AbstractNoncedBoxData<PublicKey25519Proposition, ForgerBox, ForgerBoxData> {
    private final PublicKey25519Proposition blockSignProposition;
    private final VrfPublicKey vrfPublicKey;

    public ForgerBoxData(PublicKey25519Proposition proposition,
                         long value,
                         PublicKey25519Proposition blockSignProposition,
                         VrfPublicKey vrfPublicKey) {
        super(proposition, value);
        Objects.requireNonNull(blockSignProposition, "blockSignProposition must be defined");
        Objects.requireNonNull(vrfPublicKey, "vrfPublicKey must be defined");

        this.blockSignProposition = blockSignProposition;
        this.vrfPublicKey = vrfPublicKey;
    }

    public PublicKey25519Proposition blockSignProposition() {
        return blockSignProposition;
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
                blockSignProposition().bytes(),
                vrfPublicKey().bytes());
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
        int blockSignPropositionOffset = valueOffset + Longs.BYTES;
        int vrfPubKeyOffset = blockSignPropositionOffset + PublicKey25519Proposition.getLength();

        PublicKey25519Proposition proposition = PublicKey25519PropositionSerializer.getSerializer().parseBytes(Arrays.copyOf(bytes, valueOffset));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, valueOffset, blockSignPropositionOffset));
        PublicKey25519Proposition blockSignProposition = PublicKey25519Proposition.parseBytes(Arrays.copyOfRange(bytes, blockSignPropositionOffset, vrfPubKeyOffset));
        VrfPublicKey vrfPublicKey = VrfPublicKeySerializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, vrfPubKeyOffset, bytes.length));

        return new ForgerBoxData(proposition, value, blockSignProposition, vrfPublicKey);
    }

    @Override
    public byte[] customFieldsHash() {
        return Blake2b256.hash(Bytes.concat(blockSignProposition().pubKeyBytes(), vrfPublicKey().pubKeyBytes()));
    }
}
