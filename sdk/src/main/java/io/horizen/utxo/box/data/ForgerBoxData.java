package io.horizen.utxo.box.data;

import com.google.common.primitives.Bytes;
import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.proposition.VrfPublicKey;
import io.horizen.utxo.box.ForgerBox;
import sparkz.crypto.hash.Blake2b256;

import java.util.Objects;

public final class ForgerBoxData extends AbstractBoxData<PublicKey25519Proposition, ForgerBox, ForgerBoxData> {
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
    public BoxDataSerializer serializer() {
        return ForgerBoxDataSerializer.getSerializer();
    }

    @Override
    public byte[] customFieldsHash() {
        return Blake2b256.hash(Bytes.concat(blockSignProposition().pubKeyBytes(), vrfPublicKey().pubKeyBytes()));
    }
}
