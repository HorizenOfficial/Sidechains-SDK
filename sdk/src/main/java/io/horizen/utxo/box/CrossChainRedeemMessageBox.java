package io.horizen.utxo.box;

import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.sc2sc.CrossChainMessage;
import io.horizen.utxo.box.data.CrossChainRedeemMessageBoxData;

import static io.horizen.utxo.box.CoreBoxesIdsEnum.CrossChainRedeemMessageBoxId;

public final class CrossChainRedeemMessageBox
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