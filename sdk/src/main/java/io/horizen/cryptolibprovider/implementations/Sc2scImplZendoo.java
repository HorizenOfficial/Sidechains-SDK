package io.horizen.cryptolibprovider.implementations;

import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.commitmenttreenative.ScCommitmentCertPath;
import com.horizen.librustsidechains.Constants;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.merkletreenative.InMemoryAppendOnlyMerkleTree;
import com.horizen.merkletreenative.MerklePath;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.sc2scnative.Sc2Sc;
import io.horizen.cryptolibprovider.CommonCircuit;
import io.horizen.cryptolibprovider.Sc2scCircuit;
import io.horizen.cryptolibprovider.utils.FieldElementUtils;
import io.horizen.cryptolibprovider.utils.HashUtils;
import io.horizen.sc2sc.CrossChainMessage;
import io.horizen.sc2sc.CrossChainMessageHash;
import io.horizen.sc2sc.CrossChainMessageHashImpl;
import io.horizen.utils.BytesUtils;
import io.horizen.utils.FieldElementsContainer;

import java.util.List;
import java.util.Optional;

public class Sc2scImplZendoo implements Sc2scCircuit {

    private static final int SEGMENT_SIZE = 1 << 15;
    public static final int CUSTOM_FIELDS_NUM = 32;

    @Override
    public InMemoryAppendOnlyMerkleTree initMerkleTree() {
        return InMemoryAppendOnlyMerkleTree.init(Constants.MSG_MT_HEIGHT(), 1L << Constants.MSG_MT_HEIGHT());
    }

    @Override
    public boolean generateSc2ScKeys(String provingKeyPath, String verificationKeyPath) throws Exception {
        return Sc2Sc.setup(
                ProvingSystemType.COBOUNDARY_MARLIN,
                CUSTOM_FIELDS_NUM,
                Optional.of(SEGMENT_SIZE),
                provingKeyPath,
                verificationKeyPath,
                true,
                CommonCircuit.maxProofPlusVkSize
        );
    }

    @Override
    public int getMaxMessagesPerCertificate() {
        return 1 << Constants.MSG_MT_HEIGHT();
    }

    @Override
    public byte[] getCrossChainMessageTreeRoot(InMemoryAppendOnlyMerkleTree tree) {
        try (FieldElement root = getCrossChainMessageTreeRootAsFieldElement(tree)) {
            return root.serializeFieldElement();
        }
    }

    @Override
    public FieldElement getCrossChainMessageTreeRootAsFieldElement(InMemoryAppendOnlyMerkleTree tree) {
        tree.finalizeTreeInPlace();
        return tree.root();
    }

    @Override
    public int insertMessagesInMerkleTreeWithIndex(InMemoryAppendOnlyMerkleTree tree, List<CrossChainMessage> messages, CrossChainMessage leafMsg) throws Exception {
        int msgIndexInsertion = -1;
        for (int i = 0; i < messages.size(); i++) {
            CrossChainMessage currMsg = messages.get(i);

            insertMessageInMerkleTree(tree, currMsg);

            if (currMsg.equals(leafMsg)) {
                msgIndexInsertion = i;
            }
        }
        if (msgIndexInsertion < 0) {
            throw new IllegalArgumentException("Cannot get merkle path of a message not included in the message list");
        }
        return msgIndexInsertion;
    }

    @Override
    public void insertMessagesInMerkleTree(InMemoryAppendOnlyMerkleTree msgTree, List<CrossChainMessage> messages) throws Exception {
        for (CrossChainMessage msg : messages) {
            insertMessageInMerkleTree(msgTree, msg);
        }
    }

    private void insertMessageInMerkleTree(InMemoryAppendOnlyMerkleTree msgTree, CrossChainMessage currMsg) throws Exception {
        try (
                FieldElementsContainer fieldElementsContainer = FieldElementUtils.deserializeMany(currMsg.bytes());
                FieldElement cumulatedFieldElement = HashUtils.fieldElementListHash(fieldElementsContainer.getFieldElementCollection())
        ) {
            msgTree.append(cumulatedFieldElement);
        }
    }

    @Override
    public MerklePath getCrossChainMessageMerklePath(InMemoryAppendOnlyMerkleTree tree, int leafIndex) {
        tree.finalizeTreeInPlace();
        return tree.getMerklePath(leafIndex);
    }

    @Override
    public byte[] createRedeemProof(CrossChainMessageHash messageHash,
                                    byte[] scTxCommitmentRoot,
                                    byte[] nextScTxCommitmentRoot,
                                    WithdrawalCertificate currWithdrawalCertificate,
                                    WithdrawalCertificate nextWithdrawalCertificate,
                                    ScCommitmentCertPath merklePathTopQualityCert,
                                    ScCommitmentCertPath nextMerklePathTopQualityCert,
                                    MerklePath messageMerklePath,
                                    String provingKeyPath
    ) {
        return Sc2Sc.createProof(
                BytesUtils.toMainchainFormat(nextScTxCommitmentRoot),
                BytesUtils.toMainchainFormat(scTxCommitmentRoot),
                messageHash.getValue(),
                nextWithdrawalCertificate,
                currWithdrawalCertificate,
                nextMerklePathTopQualityCert,
                merklePathTopQualityCert,
                messageMerklePath,
                provingKeyPath,
                Optional.of(SEGMENT_SIZE),
                true
        );
    }

    @Override
    public boolean verifyRedeemProof(CrossChainMessageHash messageHash,
                                     byte[] scTxCommitmentRootCertEpoch,
                                     byte[] nextScTxCommitmentRootCertEpoch,
                                     byte[] proof,
                                     String verifyKeyPath) {
        return Sc2Sc.verifyProof(
                BytesUtils.toMainchainFormat(nextScTxCommitmentRootCertEpoch),
                BytesUtils.toMainchainFormat(scTxCommitmentRootCertEpoch),
                messageHash.getValue(),
                proof,
                verifyKeyPath
        );
    }

    @Override
    public CrossChainMessageHash getCrossChainMessageHash(CrossChainMessage msg) throws Exception {
        try (
                FieldElementsContainer fieldElementsContainer = FieldElementUtils.deserializeMany(msg.bytes());
                FieldElement fe = HashUtils.fieldElementListHash(fieldElementsContainer.getFieldElementCollection())
        ) {
            return new CrossChainMessageHashImpl(fe.serializeFieldElement());
        }
    }
}
