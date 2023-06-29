package io.horizen.utxo.box;

import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.sc2sc.CrossChainProtocolVersion;
import io.horizen.utxo.box.data.CrossChainMessageBoxData;

import static io.horizen.utxo.box.CoreBoxesIdsEnum.CrossChainMessageBoxId;

public final class CrossChainMessageBox
        extends AbstractBox<PublicKey25519Proposition, CrossChainMessageBoxData, CrossChainMessageBox>
{
    public CrossChainMessageBox(CrossChainMessageBoxData boxData, long nonce) {
        super(boxData, nonce);
    }

    public CrossChainProtocolVersion getProtocolVersion(){
        return boxData.getProtocolVersion();
    }

    public int getMessageType() {
        return boxData.getMessageType();
    }

    public byte[] getReceiverSidechain() {
        return boxData.getReceiverSidechain();
    }

    public byte[] getReceiverAddress() {
        return boxData.getReceiverAddress();
    }

    public byte[] getPayload() {
        return boxData.getPayloadHash();
    }

    @Override
    public byte boxTypeId() {
        return CrossChainMessageBoxId.id();
    }

    @Override
    public BoxSerializer serializer() {
        return CrossChainMessageBoxSerializer.getSerializer();
    }

    @Override
    public Boolean isCustom() { return false; }
}

