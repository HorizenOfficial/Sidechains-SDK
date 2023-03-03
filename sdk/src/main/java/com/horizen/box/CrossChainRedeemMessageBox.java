package com.horizen.box;

import com.horizen.box.data.CrossChainRedeemMessageBoxData;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.sc2sc.CrossChainMessage;

import static com.horizen.box.CoreBoxesIdsEnum.CrossChainRedeemMessageBoxId;

public class CrossChainRedeemMessageBox
        extends AbstractBox<PublicKey25519Proposition, CrossChainRedeemMessageBoxData, CrossChainRedeemMessageBox> {
    public CrossChainRedeemMessageBox(CrossChainRedeemMessageBoxData boxData, long nonce) {
        super(boxData, nonce);
    }

    public CrossChainMessage getCrossChainMessage() {
        return boxData.getMessage();
    }

    public byte[] getCertificateDataHash() {
        return boxData.getCertificateDataHash();
    }

    public byte[] getNextCertificateDataHash() {
        return boxData.getNextCertificateDataHash();
    }

    public byte[] getScCommitmentTreeRoot() {
        return boxData.getScCommitmentTreeRoot();
    }

    public byte[] getNextScCommitmentTreeRoot() {
        return boxData.getNextScCommitmentTreeRoot();
    }

    public byte[] getProof() {
        return boxData.getProof();
    }

    @Override
    public BoxSerializer serializer() {
        return CrossChainRedeemMessageBoxSerializer.getSerializer();
    }

    @Override
    public byte boxTypeId() {
        return CrossChainRedeemMessageBoxId.id();
    }

    @Override
    public Boolean isCustom() {
        return false;
    }
}