package io.horizen.validation;

import io.horizen.SidechainSettings;
import io.horizen.cryptolibprovider.Sc2scCircuit;
import io.horizen.params.NetworkParams;
import io.horizen.sc2sc.CrossChainMessage;
import io.horizen.sc2sc.CrossChainMessageHash;
import io.horizen.utils.BytesUtils;
import io.horizen.utxo.box.data.CrossChainRedeemMessageBoxData;
import io.horizen.utxo.storage.SidechainStateStorage;
import io.horizen.utxo.transaction.AbstractCrossChainRedeemTransaction;

import java.util.Arrays;

public class AbstractCrossChainRedeemTransactionValidator implements TransactionValidator<AbstractCrossChainRedeemTransaction> {
    private final SidechainSettings sidechainSettings;
    private final SidechainStateStorage scStateStorage;
    private final Sc2scCircuit sc2scCircuit;
    private final NetworkParams networkParams;

    public AbstractCrossChainRedeemTransactionValidator(
            SidechainSettings sidechainSettings,
            SidechainStateStorage scStateStorage,
            Sc2scCircuit sc2scCircuit,
            NetworkParams networkParams
    ) {
        this.sidechainSettings = sidechainSettings;
        this.scStateStorage = scStateStorage;
        this.sc2scCircuit = sc2scCircuit;
        this.networkParams = networkParams;
    }

    @Override
    public void validate(AbstractCrossChainRedeemTransaction txToBeValidated) throws Exception {
        CrossChainRedeemMessageBoxData redeemMessageBox = txToBeValidated.getRedeemMessageBox();
        CrossChainMessage ccMsg = redeemMessageBox.getMessage();

        // CrossChainRedeemMsg.message.receivingScId = current sidechain
        validateCorrectSidechain(ccMsg);

        // hash(CrossChainMsg) has not already been redeemed
        validateMsgDoubleRedeem(ccMsg);

        // CrossChainRedeemMsg.sc_commitment_tree_root and CrossChainRedeemMsg.next_sc_commitment_tree_root have been seen before
        verifyCommitmentTreeRootAndNextCommitmentTreeRoot(
                redeemMessageBox.getScCommitmentTreeRoot(),
                redeemMessageBox.getNextScCommitmentTreeRoot()
        );

        // CrossChainRedeemMsg.proof is valid (requires zk circuit invocation)
        verifyProof(redeemMessageBox);
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

    private void validateMsgDoubleRedeem(CrossChainMessage ccMsg) throws Exception {
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

    private void verifyProof(CrossChainRedeemMessageBoxData ccMsgBoxData) throws Exception {
        CrossChainMessage ccMsg = ccMsgBoxData.getMessage();
        CrossChainMessageHash ccMsgHash = sc2scCircuit.getCrossChainMessageHash(ccMsg);
        boolean isProofVerified = sc2scCircuit.verifyRedeemProof(
                ccMsgHash,
                ccMsgBoxData.getScCommitmentTreeRoot(),
                ccMsgBoxData.getNextScCommitmentTreeRoot(),
                ccMsgBoxData.getProof(),
                networkParams.sc2ScVerificationKeyFilePath()
        );

        if (!isProofVerified) {
            throw new IllegalArgumentException(String.format("Cannot verify this cross-chain message: `%s`", ccMsg));
        }
    }
}