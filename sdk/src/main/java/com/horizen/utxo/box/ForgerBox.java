package com.horizen.utxo.box;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.horizen.utxo.box.data.ForgerBoxData;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.VrfPublicKey;

import static com.horizen.utxo.box.CoreBoxesIdsEnum.ForgerBoxId;

public final class ForgerBox
        extends AbstractBox<PublicKey25519Proposition, ForgerBoxData, ForgerBox>
        implements CoinsBox<PublicKey25519Proposition>
{

    public ForgerBox(ForgerBoxData boxData, long nonce) {
        super(boxData, nonce);
    }

    @Override
    public byte boxTypeId() {
        return ForgerBoxId.id();
    }

    @JsonProperty("vrfPubKey")
    public VrfPublicKey vrfPubKey() {
        return boxData.vrfPublicKey();
    }

    @JsonProperty("blockSignProposition")
    public PublicKey25519Proposition blockSignProposition() {
        return boxData.blockSignProposition();
    }

    @Override
    public BoxSerializer serializer() {
        return ForgerBoxSerializer.getSerializer();
    }

    @Override
    public String toString() {
        return String.format("%s(id: %s, proposition: %s, value: %d, vrfPubKey: %s, blockSignProposition: %s, nonce: %d)", this.getClass().toString(), encoder().encode(id()), proposition(), value(), vrfPubKey(), blockSignProposition(), nonce());
    }

    @Override
    public Boolean isCustom() { return false; }
}
