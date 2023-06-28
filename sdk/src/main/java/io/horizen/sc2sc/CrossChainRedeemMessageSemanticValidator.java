package io.horizen.sc2sc;

import io.horizen.account.sc2sc.AccountCrossChainRedeemMessage;
import io.horizen.utils.Constants;

public final class CrossChainRedeemMessageSemanticValidator {
    public void validateMessage(CrossChainRedeemMessage ccRedeemMsg) {
        validateCertificateDataHash(ccRedeemMsg.getCertificateDataHash());
        validateCertificateDataHash(ccRedeemMsg.getNextCertificateDataHash());
        validateScCommitmentTreeRoot(ccRedeemMsg.getScCommitmentTreeRoot());
        validateScCommitmentTreeRoot(ccRedeemMsg.getNextScCommitmentTreeRoot());
    }

    public void validateMessage(AccountCrossChainRedeemMessage accCcRedeemMsg) {
        validateCertificateDataHash(accCcRedeemMsg.certificateDataHash());
        validateCertificateDataHash(accCcRedeemMsg.nextCertificateDataHash());
        validateScCommitmentTreeRoot(accCcRedeemMsg.scCommitmentTreeRoot());
        validateScCommitmentTreeRoot(accCcRedeemMsg.nextScCommitmentTreeRoot());
    }

    private void validateCertificateDataHash(byte[] certificateDataHash) {
        if (certificateDataHash.length != Constants.CERTIFICATE_DATA_HASH_SIZE()) {
            throw new IllegalArgumentException("Certificate data hash must be 32 bytes long");
        }
    }

    private void validateScCommitmentTreeRoot(byte[] scCommitmentTreeRoot) {
        if (scCommitmentTreeRoot.length != Constants.SC_COMMITMENT_TREE_ROOT_SIZE()) {
            throw new IllegalArgumentException("Sidechain commitment tree root must be 32 bytes long");
        }
    }
}
