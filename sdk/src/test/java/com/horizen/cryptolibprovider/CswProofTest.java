package com.horizen.cryptolibprovider;

import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.cswnative.CswFtProverData;
import com.horizen.cswnative.CswProof;
import com.horizen.cswnative.CswSysData;
import com.horizen.cswnative.CswUtxoProverData;
import com.horizen.fwtnative.ForwardTransferOutput;
import com.horizen.librustsidechains.Constants;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.merkletreenative.InMemoryAppendOnlyMerkleTree;
import com.horizen.merkletreenative.MerklePath;
import com.horizen.poseidonnative.PoseidonHash;
import com.horizen.provingsystemnative.ProvingSystem;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.scutxonative.ScUtxoOutput;
import com.horizen.utils.BytesUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

@Ignore
public class CswProofTest {
    static long seed = 1234567890L;

    static int numBt = 10;
    static ScUtxoOutput scUtxoOutput;
    static ForwardTransferOutput ftOutput;
    static WithdrawalCertificate wCert;

    static String testScSecretKey = "50d5e4c0b15402013941a3c525c6af85e7ab8a2da39a59707211ddd53def965e";
    static String testScPublicKey = "f165e1e5f7c290e52f2edef3fbab60cbae74bfd3274f8e5ee1de3345c954a166";

    static String snarkPkPath = "./test_csw_snark_pk";
    static String snarkVkPath = "./test_csw_snark_vk";
    static int withdrawalEpochLength = 10;
    static int rangeSize;
    static ProvingSystemType psType = ProvingSystemType.COBOUNDARY_MARLIN;

    @BeforeClass
    public static void initKeys() {
        CommonCircuit common = new CommonCircuit();
        CswCircuit cswCircuit = new CswCircuitImplZendoo();
        rangeSize = cswCircuit.rangeSize(withdrawalEpochLength);

        // Generate keys
        assertTrue(common.generateCoboundaryMarlinDLogKeys());
        assertTrue(cswCircuit.generateCoboundaryMarlinSnarkKeys(withdrawalEpochLength, snarkPkPath, snarkVkPath));

        assertEquals(
                psType,
                ProvingSystem.getVerifierKeyProvingSystemType(snarkVkPath)
        );
        assertEquals(
                ProvingSystem.getProverKeyProvingSystemType(snarkPkPath),
                ProvingSystem.getVerifierKeyProvingSystemType(snarkVkPath)
        );



        // Generate random (but consistent) data
        Random r = new Random(seed);

        wCert = WithdrawalCertificate.getRandom(r, numBt, 0);

        scUtxoOutput = ScUtxoOutput.getRandom(r);
        scUtxoOutput.setSpendingPubKey(BytesUtils.fromHexString(testScPublicKey));

        ftOutput = ForwardTransferOutput.getRandom(r);
        ftOutput.setReceiverPubKey(BytesUtils.fromHexString(testScPublicKey));
    }

    @Test
    public void testCreateVerifyScUtxoRandomProof() throws Exception {
        // Compute scUtxo nullifier
        FieldElement nullifier = scUtxoOutput.getNullifier();

        // Put ScUtxo in MerkleTree just to generate valid root
        // and path that will be used to update the data
        InMemoryAppendOnlyMerkleTree mht = InMemoryAppendOnlyMerkleTree.init(Constants.SC_MST_HEIGHT(), 1);
        mht.append(nullifier);
        mht.finalizeTreeInPlace();

        FieldElement mstRoot = mht.root();
        MerklePath mstPathToOutput = mht.getMerklePath(0);
        mht.close(); // Free tree as we don't need it anymore

        // Split the mstRoot into 2 FieldElements to be declared as custom fields and put them inside wCert
        List<FieldElement> customFields = mstRoot.splitAt(Constants.FIELD_ELEMENT_LENGTH()/2);
        mstRoot.close(); // Free after split, we don't need it anymore
        wCert.setCustomFields(customFields.toArray(new FieldElement[0]));

        // Generate CswUtxoProverData
        CswUtxoProverData utxoData = new CswUtxoProverData(
                scUtxoOutput,
                BytesUtils.fromHexString(testScSecretKey),
                mstPathToOutput
        );

        // Compute wCert hash
        FieldElement scLastWcertHash = wCert.getHash();

        // Generate random receiver
        Random r = new Random(seed);
        byte[] receiver = new byte[Constants.MC_PK_HASH_SIZE()];
        r.nextBytes(receiver);

        // Generate CswSysData
        CswSysData sysData = new CswSysData(
                Optional.of(FieldElement.createRandom(r)),
                Optional.of(scLastWcertHash),
                Optional.empty(),
                scUtxoOutput.getAmount(),
                nullifier,
                receiver
        );

        // Create proof
        byte[] proof = CswProof.createProof(
                rangeSize, 2, sysData, wCert.getScId(),
                Optional.of(wCert), Optional.of(utxoData), Optional.empty(), snarkPkPath
        );

        // Proof verification must be successfull
        assertTrue(CswProof.verifyProof(sysData, wCert.getScId(), proof, snarkVkPath));

        // Change one of the params and assert failure
        assertFalse(CswProof.verifyProof(sysData, FieldElement.createRandom(r), proof, snarkVkPath));

        // Free local data
        utxoData.close();
        sysData.close();
    }

    @Test
    public void testCreateVerifyFwtRandomProof() throws Exception {
        // Generate random receiver
        Random r = new Random(seed);
        byte[] receiver = new byte[Constants.MC_PK_HASH_SIZE()];
        r.nextBytes(receiver);

        // Compute ftOutput nullifier
        FieldElement nullifier = ftOutput.getNullifier();

        // Put ftOutput in MerkleTree just to generate valid root and path
        InMemoryAppendOnlyMerkleTree mht = InMemoryAppendOnlyMerkleTree.init(Constants.SC_COMM_TREE_FT_SUBTREE_HEIGHT(), 1);
        mht.append(nullifier);
        mht.finalizeTreeInPlace();

        FieldElement ftTreeRoot = mht.root();
        MerklePath ftTreePath = mht.getMerklePath(0);
        mht.close(); // Free tree as we don't need it anymore

        // Sample random data and compute scHash
        FieldElement scCreationCommitment = FieldElement.createRandom(r);
        FieldElement scbBtrTreeRoot = FieldElement.createRandom(r);
        FieldElement wCertTreeRoot = FieldElement.createRandom(r);
        PoseidonHash h = PoseidonHash.getInstanceConstantLength(5);
        h.update(ftTreeRoot);
        h.update(scbBtrTreeRoot);
        h.update(wCertTreeRoot);
        h.update(scCreationCommitment);
        h.update(wCert.getScId());
        FieldElement scHash = h.finalizeHash();
        h.close(); // We don't need PoseidonHash instance anymroe

        // Put scHash in MerkleTree just to generate valid root and path
        InMemoryAppendOnlyMerkleTree mhtNew = InMemoryAppendOnlyMerkleTree.init(Constants.SC_COMM_TREE_HEIGHT(), 1);
        mhtNew.append(scHash);
        mhtNew.finalizeTreeInPlace();

        FieldElement scTxsComTreeRoot = mhtNew.root();
        MerklePath merklePathToScHash = mhtNew.getMerklePath(0);
        mhtNew.close(); // Free tree as we don't need it anymore

        // Now generate scTxsComHashes list, putting scTxsComTreeRoot in one of them
        // and contextually compute mcbScTxsComEnd
        FieldElement mcbScTxsComStart = FieldElement.createRandom(r);
        int scTxsComTreeRootPosition = r.nextInt(rangeSize);

        FieldElement acc = mcbScTxsComStart;
        PoseidonHash h1 = PoseidonHash.getInstanceConstantLength(2);

        List<FieldElement> scTxsComHashes = new ArrayList<>();
        for (int i = 0; i < rangeSize; i++) {
            // Generate random FieldElement
            FieldElement scTxsComHash = (i == scTxsComTreeRootPosition) ? scTxsComTreeRoot: FieldElement.createRandom(r);

            // Add it to the list
            scTxsComHashes.add(scTxsComHash);

            // Update acc
            h1.update(acc);
            h1.update(scTxsComHash);
            acc = h1.finalizeHash();
            h1.reset();
        }
        FieldElement mcbScTxsComEnd = acc;

        // Generate FtCswProverData
        CswFtProverData ftData = new CswFtProverData(
                ftOutput,
                BytesUtils.fromHexString(testScSecretKey),
                mcbScTxsComStart,
                merklePathToScHash,
                ftTreePath,
                scCreationCommitment,
                scbBtrTreeRoot,
                wCertTreeRoot,
                scTxsComHashes
        );

        // Generate CswSysData
        CswSysData sysData = new CswSysData(
                Optional.of(FieldElement.createRandom(r)),
                Optional.empty(),
                Optional.of(mcbScTxsComEnd),
                ftOutput.getAmount(),
                nullifier,
                receiver
        );

        // Create proof
        byte[] proof = CswProof.createProof(
                rangeSize, 2, sysData, wCert.getScId(), Optional.empty(),
                Optional.empty(), Optional.of(ftData), snarkPkPath
        );

        // Proof verification must be successfull
        assertTrue(CswProof.verifyProof(sysData, wCert.getScId(), proof, snarkVkPath));

        // Change one of the params and assert failure
        assertFalse(CswProof.verifyProof(sysData, FieldElement.createRandom(r), proof, snarkVkPath));

        // Free local data
        ftTreeRoot.close();
        scHash.close();
        ftData.close();
        sysData.close();
    }

    @AfterClass
    public static void free() throws Exception {
        wCert.close();
        // Delete proving keys and verification keys
        new File(snarkPkPath).delete();
        new File(snarkVkPath).delete();
    }
}
