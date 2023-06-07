package io.horizen.cryptolibprovider.implementations;

import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.commitmenttreenative.CommitmentTree;
import com.horizen.commitmenttreenative.CustomBitvectorElementsConfig;
import com.horizen.commitmenttreenative.CustomFieldElementsConfig;
import com.horizen.commitmenttreenative.ScCommitmentCertPath;
import com.horizen.librustsidechains.Constants;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.merkletreenative.InMemoryAppendOnlyMerkleTree;
import com.horizen.merkletreenative.MerklePath;
import com.horizen.provingsystemnative.ProvingSystem;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.sc2scnative.Sc2Sc;
import io.horizen.crosschain.CrossChainMessageMerkleTree;
import io.horizen.cryptolibprovider.CommonCircuit;
import io.horizen.cryptolibprovider.Sc2scCircuit;
import io.horizen.sc2sc.CrossChainMessage;
import io.horizen.sc2sc.CrossChainMessageHash;
import io.horizen.sc2sc.CrossChainProtocolVersion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import scala.collection.JavaConverters;
import scala.collection.Seq;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.assertTrue;

public class Sc2scImplZendooTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void assertKeysAreCorrectlyGenerated() throws Exception {
        // Arrange
        File tempProvingKeyFile = folder.newFile("tempProvingKeyFile");
        File tempVerificationKeyFile = folder.newFile("tempVerificationKeyFile");
        CommonCircuit cc = new CommonCircuit();
        Sc2scCircuit circuit = new Sc2scImplZendoo();

        // Act
        cc.generateCoboundaryMarlinDLogKeys();
        boolean keysWereGenerated = circuit.generateSc2ScKeys(tempProvingKeyFile.getPath(), tempVerificationKeyFile.getPath());

        // Assert
        assertTrue(keysWereGenerated);
    }

    @Test
    public void generateAndVerifyProof() throws Exception {
        File tempProvingKeyFile = folder.newFile("tempProvingKeyFile");
        String provingKeyPath = tempProvingKeyFile.getPath();

        File tempVerificationKeyFile = folder.newFile("tempVerificationKeyFile");
        String verificationKeyPath = tempVerificationKeyFile.getPath();

        Sc2scImplZendoo circuit = new Sc2scImplZendoo();

        ProvingSystem.generateDLogKeys(ProvingSystemType.COBOUNDARY_MARLIN, Sc2scImplZendoo.SEGMENT_SIZE);
        assertTrue(circuit.generateSc2ScKeys(provingKeyPath, verificationKeyPath));

        try (
                InMemoryAppendOnlyMerkleTree tree = InMemoryAppendOnlyMerkleTree.init(Constants.MSG_MT_HEIGHT(), 1L << Constants.MSG_MT_HEIGHT());
                CommitmentTree currentCt = CommitmentTree.init();
                CommitmentTree nextCt = CommitmentTree.init();
        ) {
            Random r = new Random();
            RandomProofData randomProofData = generateRandomProof(r, tree, currentCt, nextCt, provingKeyPath, circuit);

            assertTrue(Sc2Sc.verifyProof(
                    randomProofData.nextScTxCommitmentsRoot,
                    randomProofData.currentScTxCommitmentsRoot,
                    randomProofData.msgHash,
                    randomProofData.proof,
                    verificationKeyPath
            ));
        }
    }

    private RandomProofData generateRandomProof(
            Random r,
            InMemoryAppendOnlyMerkleTree msgTree,
            CommitmentTree currentCt,
            CommitmentTree nextCt,
            String provingKeyPath,
            Sc2scImplZendoo circuit
    ) throws Exception {
        // We'll create tx commitment for a epoch with one certificate and
        // another one for next epoch with another certificate. From this
        // commitments we'll extract the path and then create a circuit.
        CrossChainMessage msg1 = new CrossChainMessage(
                CrossChainProtocolVersion.VERSION_1,
                1,
                generateRandomBytes(r, 32),
                generateRandomBytes(r, 32),
                generateRandomBytes(r, 32),
                generateRandomBytes(r, 32),
                "payload1".getBytes()
        );

        CrossChainMessageMerkleTree ccMsgMerkleTree = new CrossChainMessageMerkleTree();

        byte[] scId = generateFieldRandomBytes(r);
        Seq<CrossChainMessage> messages = JavaConverters.asScalaIteratorConverter(List.of(msg1).iterator()).asScala().toSeq();
        int msgTreeIndex = ccMsgMerkleTree.insertMessagesInMerkleTreeWithIndex(msgTree, messages, msg1);
        CrossChainMessageHash msgHash = msg1.getCrossChainMessageHash();
        byte[] msgRoot = ccMsgMerkleTree.getCrossChainMessageTreeRoot(msgTree);

        WithdrawalCertificate currWithdrawalCertificate = WithdrawalCertificate.getRandom(
                r, 0, Sc2scImplZendoo.CUSTOM_FIELDS_NUM);
        currWithdrawalCertificate.setScId(scId);
        currWithdrawalCertificate.setEpochNumber(42);

        currWithdrawalCertificate.setCustomField(Constants.MSG_ROOT_HASH_CUSTOM_FIELDS_POS(), msgRoot);
        byte[] currCertHash = currWithdrawalCertificate.getHashBytes();

        addRandomScc(r, currentCt, scId);
        currentCt.addCertLeaf(scId, currCertHash);

        byte[] currentScTxCommitmentsRoot = root(currentCt);

        WithdrawalCertificate nextWithdrawalCertificate = WithdrawalCertificate.getRandom(
                r, 0, Sc2scImplZendoo.CUSTOM_FIELDS_NUM);
        nextWithdrawalCertificate.setScId(scId);
        nextWithdrawalCertificate.setEpochNumber(42 + 1);

        nextWithdrawalCertificate.setCustomField(Constants.MAX_QUALITY_CERT_HASH_CUSTOM_FIELDS_POS(), currCertHash);
        byte[] nextCertHash = nextWithdrawalCertificate.getHashBytes();

        addRandomScc(r, nextCt, scId);
        nextCt.addCertLeaf(scId, nextCertHash);
        byte[] nextScTxCommitmentsRoot = root(nextCt);

        try (
                ScCommitmentCertPath currentPath = currentCt.getScCommitmentCertPath(scId, currCertHash).get();
                ScCommitmentCertPath nextPath = nextCt.getScCommitmentCertPath(scId, nextCertHash).get();
                MerklePath msgPath = ccMsgMerkleTree.getCrossChainMessageMerklePath(msgTree, msgTreeIndex)
        ) {
            byte[] proof = circuit.createRedeemProof(
                    msgHash,
                    currentScTxCommitmentsRoot,
                    nextScTxCommitmentsRoot,
                    currWithdrawalCertificate,
                    nextWithdrawalCertificate,
                    currentPath,
                    nextPath,
                    msgPath,
                    provingKeyPath
            );

            return new RandomProofData(nextScTxCommitmentsRoot, currentScTxCommitmentsRoot, msgHash.getValue(), proof);
        }
    }

    private static class RandomProofData {
        public byte[] nextScTxCommitmentsRoot;
        public byte[] currentScTxCommitmentsRoot;
        public byte[] msgHash;
        public byte[] proof;

        public RandomProofData(byte[] nextScTxCommitmentsRoot, byte[] currentScTxCommitmentsRoot,
                               byte[] msgHash, byte[] proof) {
            this.nextScTxCommitmentsRoot = nextScTxCommitmentsRoot;
            this.currentScTxCommitmentsRoot = currentScTxCommitmentsRoot;
            this.msgHash = msgHash;
            this.proof = proof;
        }
    }

    private void addRandomScc(Random r, CommitmentTree ct, byte[] scId) {
        assertTrue(ct.addScCr(scId,
                r.nextLong(),
                generateFieldRandomBytes(r),
                generateFieldRandomBytes(r),
                r.nextInt(),
                r.nextInt(),
                (byte) r.nextInt(),
                new CustomFieldElementsConfig[0],
                new CustomBitvectorElementsConfig[0],
                r.nextLong(),
                r.nextLong(),
                generateRandomBytes(r, 1024),
                Optional.empty(),
                generateRandomBytes(r, 2000),
                Optional.empty()));
    }

    private byte[] generateRandomBytes(Random r, int len) {
        byte[] bytes = new byte[len];
        r.nextBytes(bytes);
        return bytes;
    }

    private byte[] generateFieldRandomBytes(Random r) {
        try (FieldElement f = FieldElement.createRandom(r)) {
            return f.serializeFieldElement();
        }
    }

    private byte[] root(CommitmentTree ct) {
        if (ct.getCommitment().isPresent()) {
            try (FieldElement root = ct.getCommitment().get()) {
                return root.serializeFieldElement();
            }
        } else {
            return new byte[0];
        }
    }
}