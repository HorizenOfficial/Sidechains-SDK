package com.horizen.box;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.data.CertifierRightBoxData;
import com.horizen.box.data.CertifierRightBoxDataSerializer;
import com.horizen.proposition.PublicKey25519Proposition;

import java.util.Arrays;

import static com.horizen.box.CoreBoxesIdsEnum.CertifierRightBoxId;

// CertifierLock coins are not transmitted to SC, so CertifierRightBox is not a CoinsBox
// CertifierRightBox can be opened starting from specified Withdrawal epoch.
public final class CertifierRightBox
    extends AbstractNoncedBox<PublicKey25519Proposition, CertifierRightBoxData, CertifierRightBox>
{
    public CertifierRightBox(CertifierRightBoxData boxData, long nonce) {
        super(boxData, nonce);
    }

    @JsonProperty("activeFromWithdrawalEpoch")
    public long activeFromWithdrawalEpoch() {
        return boxData.activeFromWithdrawalEpoch();
    }

    @Override
    public byte boxTypeId() {
        return CertifierRightBoxId.id();
    }

    @Override
    public String toString() {
        return String.format("%s(id: %s, proposition: %s, nonce: %d, epoch: %d)", this.getClass().toString(), encoder().encode(id()), proposition(), nonce(), activeFromWithdrawalEpoch());
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(
                Longs.toByteArray(nonce),
                CertifierRightBoxDataSerializer.getSerializer().toBytes(boxData)
        );
    }

    @Override
    public BoxSerializer serializer() {
        return CertifierRightBoxSerializer.getSerializer();
    }

    public static CertifierRightBox parseBytes(byte[] bytes) {
        long nonce = Longs.fromByteArray(Arrays.copyOf(bytes, Longs.BYTES));
        CertifierRightBoxData boxData = CertifierRightBoxDataSerializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, Longs.BYTES, bytes.length));

        return new CertifierRightBox(boxData, nonce);
    }

}
