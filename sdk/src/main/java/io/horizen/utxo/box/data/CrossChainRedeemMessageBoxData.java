package io.horizen.utxo.box.data;

import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.sc2sc.CrossChainMessage;
import io.horizen.utxo.box.CrossChainRedeemMessageBox;

public class CrossChainRedeemMessageBoxData
        extends AbstractBoxData<PublicKey25519Proposition, CrossChainRedeemMessageBox, CrossChainRedeemMessageBoxData> {
    private final CrossChainMessage message;
    private final byte[] certificateDataHash;
    private final byte[] nextCertificateDataHash;
    private final byte[] scCommitmentTreeRoot;
    private final byte[] nextScCommitmentTreeRoot;
    private final byte[] proof;

    public  CrossChainRedeemMessageBoxData(
            PublicKey25519Proposition proposition,
            CrossChainMessage message,
            byte[] certificateDataHash,
            byte[] nextCertificateDataHash,
            byte[] scCommitmentTreeRoot,
            byte[] nextScCommitmentTreeRoot,
            byte[] proof
    ) {
        super(proposition, 0);
        this.message = message;
        this.certificateDataHash = certificateDataHash;
        this.nextCertificateDataHash = nextCertificateDataHash;
        this.scCommitmentTreeRoot = scCommitmentTreeRoot;
        this.nextScCommitmentTreeRoot = nextScCommitmentTreeRoot;
        this.proof = proof;
    }

    public CrossChainMessage getMessage() {
        return message;
    }

    public byte[] getCertificateDataHash() {
        return certificateDataHash;
    }

    public byte[] getNextCertificateDataHash() {
        return nextCertificateDataHash;
    }

    public byte[] getScCommitmentTreeRoot() {
        return scCommitmentTreeRoot;
    }

    public byte[] getNextScCommitmentTreeRoot() {
        return nextScCommitmentTreeRoot;
    }

    public byte[] getProof() {
        return proof;
    }

    @Override
    public CrossChainRedeemMessageBox getBox(long nonce) {
        return new CrossChainRedeemMessageBox(this, nonce);
    }

    @Override
    public BoxDataSerializer serializer() {
        return CrossChainRedeemMessageBoxDataSerializer.getSerializer();
    }
}