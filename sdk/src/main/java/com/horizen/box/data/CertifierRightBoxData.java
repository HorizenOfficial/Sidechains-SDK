package com.horizen.box.data;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.CertifierRightBox;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import scorex.crypto.hash.Blake2b256;

import java.util.Arrays;

import static com.horizen.box.data.CoreBoxesDataIdsEnum.CertifierRightBoxDataId;

public final class CertifierRightBoxData extends AbstractNoncedBoxData<PublicKey25519Proposition, CertifierRightBox, CertifierRightBoxData> {
    private final long activeFromWithdrawalEpoch;

    public CertifierRightBoxData(PublicKey25519Proposition proposition, long value, long activeFromWithdrawalEpoch) {
        super(proposition, value);
        this.activeFromWithdrawalEpoch = activeFromWithdrawalEpoch;
    }

    public long activeFromWithdrawalEpoch() {
        return activeFromWithdrawalEpoch;
    }

    @Override
    public CertifierRightBox getBox(long nonce) {
        return new CertifierRightBox(this, nonce);
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(
                proposition().bytes(),
                Longs.toByteArray(value()),
                Longs.toByteArray(activeFromWithdrawalEpoch())
        );
    }

    @Override
    public NoncedBoxDataSerializer serializer() {
        return CertifierRightBoxDataSerializer.getSerializer();
    }

    @Override
    public byte boxDataTypeId() {
        return CertifierRightBoxDataId.id();
    }

    public static CertifierRightBoxData parseBytes(byte[] bytes) {
        int valueOffset = PublicKey25519Proposition.getLength();
        int activeOffset = valueOffset + Longs.BYTES;

        PublicKey25519Proposition proposition = PublicKey25519PropositionSerializer.getSerializer().parseBytes(Arrays.copyOf(bytes, valueOffset));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, valueOffset, activeOffset));
        long activeFromWithdrawalEpoch = Longs.fromByteArray(Arrays.copyOfRange(bytes, activeOffset, activeOffset + Longs.BYTES));

        return new CertifierRightBoxData(proposition, value, activeFromWithdrawalEpoch);
    }

    @Override
    public byte[] customFieldsHash() {
        return Blake2b256.hash(Longs.toByteArray(activeFromWithdrawalEpoch()));
    }
}
