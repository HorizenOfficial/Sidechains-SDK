package com.horizen.box;

import com.horizen.box.data.CrossChainMessageBoxData;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.sc2sc.CrossChainProtocolVersion;

import static com.horizen.box.CoreBoxesIdsEnum.CrossChainMessageBoxId;

public final class CrossChainMessageBox
        extends AbstractBox<PublicKey25519Proposition, CrossChainMessageBoxData, CrossChainMessageBox>
{
    public CrossChainMessageBox(CrossChainMessageBoxData boxData, long nonce) {
        super(boxData, nonce);
    }

    public CrossChainProtocolVersion getProtocolVersion(){
        return boxData.getProtocolVersion();
    }

    public Integer getMessageType() {
        return boxData.getMessageType();
    }

    public byte[] getReceiverSidechain() {
        return boxData.getReceiverSidechain();
    }

    public byte[] getReceiverAddress() {
        return boxData.getReceiverAddress();
    }

    public byte[] getPayload() {
        return boxData.getPayload();
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

