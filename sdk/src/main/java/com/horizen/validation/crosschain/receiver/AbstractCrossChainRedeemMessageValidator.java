package com.horizen.validation.crosschain.receiver;

import com.horizen.SidechainSettings;
import com.horizen.box.data.CrossChainRedeemMessageBoxData;
import com.horizen.cryptolibprovider.Sc2scCircuit;
import com.horizen.cryptolibprovider.utils.FieldElementUtils;
import com.horizen.merkletreenative.MerklePath;
import com.horizen.sc2sc.CrossChainMessage;
import com.horizen.sc2sc.CrossChainMessageHash;
import com.horizen.storage.SidechainStateStorage;
import com.horizen.utils.BytesUtils;
import com.horizen.validation.crosschain.CrossChainValidator;

import java.util.Arrays;

public class AbstractCrossChainRedeemMessageValidator implements CrossChainValidator<CrossChainRedeemMessageBodyToValidate> {
    private final SidechainSettings sidechainSettings;
    private final SidechainStateStorage scStateStorage;
    private final Sc2scCircuit sc2scCircuit;

    public AbstractCrossChainRedeemMessageValidator(
            SidechainSettings sidechainSettings,
            SidechainStateStorage scStateStorage,
            Sc2scCircuit sc2scCircuit
    ) {
        this.sidechainSettings = sidechainSettings;
        this.scStateStorage = scStateStorage;
        this.sc2scCircuit = sc2scCircuit;
    }

    @Override
    public void validate(CrossChainRedeemMessageBodyToValidate objectToValidate) {
        CrossChainRedeemMessageBoxData boxData = objectToValidate.getBodyToValidate();
        CrossChainMessage ccMsg = boxData.getMessage();

        // CrossChainRedeemMsg.message.receivingScId = current sidechain
        validateCorrectSidechain(ccMsg);

        // hash(CrossChainMsg) has not already been redeemed
        validateMsgDoubleRedeem(ccMsg);

        // CrossChainRedeemMsg.sc_commitment_tree_root and CrossChainRedeemMsg.next_sc_commitment_tree_root have been seen before
        verifyCommitmentTreeRootAndNextCommitmentTreeRoot(
                boxData.getScCommitmentTreeRoot(),
                boxData.getNextScCommitmentTreeRoot()
        );

        // CrossChainRedeemMsg.proof is valid (requires zk circuit invocation)
        verifyProof(boxData);
    }

    private void validateCorrectSidechain(CrossChainMessage msg) {
        byte[] receivingSidechain = msg.getReceiverSidechain();
        String receivingSidechainAsString = BytesUtils.toHexString(receivingSidechain);
        String sidechainId = sidechainSettings.genesisData().scId();

        if (!receivingSidechainAsString.equals(sidechainId)) {
            throw new IllegalArgumentException(
                    String.format("Receiver sidechain id `%s` does not match with this sidechain id `%s`", receivingSidechainAsString, sidechainId)
            );
        }
    }

    private void validateMsgDoubleRedeem(CrossChainMessage ccMsg) {
        CrossChainMessageHash currentMsgHash = sc2scCircuit.getCrossChainMessageHash(ccMsg);
        boolean ccMsgFromRedeemAlreadyExists = scStateStorage.doesCrossChainMessageHashFromRedeemMessageExist(currentMsgHash);
        if (ccMsgFromRedeemAlreadyExists) {
            throw new IllegalArgumentException(String.format("The message `%s` has already been redeemed", ccMsg));
        }
    }

    private void verifyCommitmentTreeRootAndNextCommitmentTreeRoot(byte[] scCommitmentTreeRoot, byte[] nextScCommitmentTreeRoot) {
        if (!scStateStorage.doesScTxCommitmentTreeRootExist(scCommitmentTreeRoot)) {
            throw new IllegalArgumentException(String.format("Sidechain commitment tree root `%s` does not exist", Arrays.toString(scCommitmentTreeRoot)));
        }

        if (!scStateStorage.doesScTxCommitmentTreeRootExist(nextScCommitmentTreeRoot)) {
            throw new IllegalArgumentException(String.format("Next sidechain commitment tree root `%s` does not exist", Arrays.toString(nextScCommitmentTreeRoot)));
        }
    }

    private void verifyProof(CrossChainRedeemMessageBoxData ccMsgBoxData) {
        CrossChainMessage ccMsg = ccMsgBoxData.getMessage();
        CrossChainMessageHash ccMsgHash = sc2scCircuit.getCrossChainMessageHash(ccMsg);
        boolean isProofVerified = sc2scCircuit.verifyRedeemProof(
                ccMsgHash,
                FieldElementUtils.messageToFieldElement(ccMsgBoxData.getScCommitmentTreeRoot()),
                FieldElementUtils.messageToFieldElement(ccMsgBoxData.getNextScCommitmentTreeRoot()),
                ccMsgBoxData.getProof()
        );

        if (!isProofVerified) {
            throw new IllegalArgumentException(String.format("Cannot verify this cross-chain message: `%s`", ccMsg));
        }
    }
}
