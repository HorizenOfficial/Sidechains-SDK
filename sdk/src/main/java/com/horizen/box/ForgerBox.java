package com.horizen.box;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.data.ForgerBoxData;
import com.horizen.box.data.ForgerBoxDataSerializer;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.vrf.VRFPublicKey;

import java.util.Arrays;
import java.util.Objects;

public final class ForgerBox
        extends AbstractNoncedBox<PublicKey25519Proposition, ForgerBoxData>
        implements CoinsBox<PublicKey25519Proposition>
{
    public static final byte BOX_TYPE_ID = 3;

    public ForgerBox(ForgerBoxData boxData, long nonce)
    {
        super(boxData, nonce);
    }

    @Override
    public byte boxTypeId() {
        return BOX_TYPE_ID;
    }

    @JsonProperty("vrfPubKey")
    public VRFPublicKey vrfPubKey() {
        return boxData.vrfPublicKey();
    }

    @JsonProperty("rewardProposition")
    public PublicKey25519Proposition rewardProposition() {
        return boxData.rewardProposition();
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(Longs.toByteArray(nonce), ForgerBoxDataSerializer.getSerializer().toBytes(boxData));
    }

    @Override
    public BoxSerializer serializer() {
        return ForgerBoxSerializer.getSerializer();
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj))
            return false;
        ForgerBox forgerBox = (ForgerBox) obj;
        return vrfPubKey().equals(forgerBox.vrfPubKey()) &&
                rewardProposition().equals(forgerBox.rewardProposition());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), vrfPubKey(), rewardProposition());
    }

    @Override
    public String toString() {
        return String.format("%s(id: %s, proposition: %s, value: %d, vrfPubKey: %s, rewardProposition: %s, nonce: %d)", this.getClass().toString(), encoder().encode(id()), proposition(), value(), vrfPubKey(), rewardProposition(), nonce());
    }

    public static ForgerBox parseBytes(byte[] bytes) {
        long nonce = Longs.fromByteArray(Arrays.copyOf(bytes, Longs.BYTES));
        ForgerBoxData boxData = ForgerBoxDataSerializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, Longs.BYTES, bytes.length));

        return new ForgerBox(boxData, nonce);
    }
}
