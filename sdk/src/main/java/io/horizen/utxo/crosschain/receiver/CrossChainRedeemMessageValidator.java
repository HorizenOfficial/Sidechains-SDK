package io.horizen.utxo.crosschain.receiver;

import io.horizen.cryptolibprovider.Sc2scCircuit;
import io.horizen.params.NetworkParams;
import io.horizen.proposition.Proposition;
import io.horizen.sc2sc.CrossChainMessage;
import io.horizen.sc2sc.CrossChainMessageHash;
import io.horizen.utils.BytesUtils;
import io.horizen.utxo.block.SidechainBlock;
import io.horizen.utxo.box.Box;
import io.horizen.utxo.box.data.CrossChainRedeemMessageBoxData;
import io.horizen.utxo.crosschain.CrossChainValidator;
import io.horizen.utxo.storage.SidechainStateStorage;
import io.horizen.utxo.transaction.AbstractCrossChainRedeemTransaction;
import io.horizen.utxo.transaction.BoxTransaction;
import scala.collection.JavaConverters;

public class CrossChainRedeemMessageValidator implements CrossChainValidator<SidechainBlock> {
    private final SidechainStateStorage scStateStorage;
    private final Sc2scCircuit sc2scCircuit;
    private final NetworkParams networkParams;

    public CrossChainRedeemMessageValidator(
            SidechainStateStorage scStateStorage,
            Sc2scCircuit sc2scCircuit,
            NetworkParams networkParams
    ) {
        this.scStateStorage = scStateStorage;
        this.sc2scCircuit = sc2scCircuit;
        this.networkParams = networkParams;
    }

    @Override
    public void validate(SidechainBlock objectToValidate) throws Exception {
        for (BoxTransaction<Proposition, Box<Proposition>> tx : JavaConverters.seqAsJavaList(objectToValidate.transactions())) {
            if (tx instanceof AbstractCrossChainRedeemTransaction) {
                CrossChainRedeemMessageBoxData boxData = ((AbstractCrossChainRedeemTransaction) tx).getRedeemMessageBoxData();
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
        }
    }

    private void validateCorrectSidechain(CrossChainMessage msg) {
        byte[] receivingSidechain = msg.getReceiverSidechain();
        String receivingSidechainAsString = BytesUtils.toHexString(receivingSidechain);
        String sidechainId = BytesUtils.toHexString(BytesUtils.toMainchainFormat(networkParams.sidechainId()));

        if (!receivingSidechainAsString.equals(sidechainId)) {
            throw new IllegalArgumentException(
                    String.format("Receiver sidechain id `%s` does not match with this sidechain id `%s`", receivingSidechainAsString, sidechainId)
            );
        }
    }

    private void validateMsgDoubleRedeem(CrossChainMessage ccMsg) throws Exception {
        CrossChainMessageHash currentMsgHash = ccMsg.getCrossChainMessageHash();
        boolean ccMsgFromRedeemAlreadyExists = scStateStorage.doesCrossChainMessageHashFromRedeemMessageExist(currentMsgHash);
        if (ccMsgFromRedeemAlreadyExists) {
            throw new IllegalArgumentException(String.format("The message `%s` has already been redeemed", ccMsg));
        }
    }

    private void verifyCommitmentTreeRootAndNextCommitmentTreeRoot(byte[] scCommitmentTreeRoot, byte[] nextScCommitmentTreeRoot) {
        if (!scStateStorage.doesScTxCommitmentTreeRootExist(scCommitmentTreeRoot)) {
            throw new IllegalArgumentException(String.format("Sidechain commitment tree root `%s` does not exist", BytesUtils.toHexString(scCommitmentTreeRoot)));
        }

        if (!scStateStorage.doesScTxCommitmentTreeRootExist(nextScCommitmentTreeRoot)) {
            throw new IllegalArgumentException(String.format("Next sidechain commitment tree root `%s` does not exist", BytesUtils.toHexString(nextScCommitmentTreeRoot)));
        }
    }

    private void verifyProof(CrossChainRedeemMessageBoxData ccMsgBoxData) throws Exception {
        CrossChainMessage ccMsg = ccMsgBoxData.getMessage();
        CrossChainMessageHash ccMsgHash = ccMsg.getCrossChainMessageHash();
        boolean isProofVerified = sc2scCircuit.verifyRedeemProof(
                ccMsgHash,
                ccMsgBoxData.getScCommitmentTreeRoot(),
                ccMsgBoxData.getNextScCommitmentTreeRoot(),
                ccMsgBoxData.getProof(),
                networkParams.sc2ScVerificationKeyFilePath().get()
        );

        if (!isProofVerified) {
            throw new IllegalArgumentException(String.format("Cannot verify this cross-chain message: `%s`", ccMsg));
        }
    }
}
