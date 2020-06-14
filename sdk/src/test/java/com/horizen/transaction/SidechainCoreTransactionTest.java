package com.horizen.transaction;

import com.horizen.box.NoncedBox;
import com.horizen.box.data.NoncedBoxData;
import com.horizen.companion.SidechainBoxesDataCompanion;
import com.horizen.companion.SidechainProofsCompanion;
import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

public class SidechainCoreTransactionTest extends BoxFixtureClass {

    @Test
    public void creation() {
        List<byte[]> inputsIds = Arrays.asList(getRandomBoxId(), getRandomBoxId());

        List<NoncedBoxData<Proposition, NoncedBox<Proposition>>> outputsData = new ArrayList<>();
        outputsData.add((NoncedBoxData)getRegularBoxData());
        outputsData.add((NoncedBoxData)getForgerBoxData());
        outputsData.add((NoncedBoxData)getWithdrawalRequestBoxData());

        List<Proof<Proposition>> proofs = new ArrayList<>();
        proofs.add((Proof)getRandomSignature25519());
        proofs.add((Proof)getRandomSignature25519());

        long fee = 100L;
        long timestamp = 13213L;

        SidechainBoxesDataCompanion boxesDataCompanion = new SidechainBoxesDataCompanion(new HashMap<>());
        SidechainProofsCompanion proofsCompanion = new SidechainProofsCompanion(new HashMap<>());


        // Test 1: everything is correct
        boolean exceptionOccurred = false;
        try {
            new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, timestamp, boxesDataCompanion, proofsCompanion);
        }
        catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertFalse("Test1: Successful SidechainCoreTransaction creation expected.", exceptionOccurred);


        // Test 2: inputs ids is null
        exceptionOccurred = false;
        try {
            new SidechainCoreTransaction(null, outputsData, proofs, fee, timestamp, boxesDataCompanion, proofsCompanion);
        }
        catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test2: Exception during SidechainCoreTransaction creation expected", exceptionOccurred);


        // Test 3: outputs data is null
        exceptionOccurred = false;
        try {
            new SidechainCoreTransaction(inputsIds, null, proofs, fee, timestamp, boxesDataCompanion, proofsCompanion);
        }
        catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test3: Exception during SidechainCoreTransaction creation expected", exceptionOccurred);


        // Test 4: proofs is null
        exceptionOccurred = false;
        try {
            new SidechainCoreTransaction(inputsIds, outputsData, null, fee, timestamp, boxesDataCompanion, proofsCompanion);
        }
        catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test4: Exception during SidechainCoreTransaction creation expected", exceptionOccurred);


        // Test 5: boxes data companion is null
        exceptionOccurred = false;
        try {
            new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, timestamp, null, proofsCompanion);
        }
        catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test5: Exception during SidechainCoreTransaction creation expected", exceptionOccurred);


        // Test 6: proofs companion is null
        exceptionOccurred = false;
        try {
            new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, timestamp, boxesDataCompanion, null);
        }
        catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test6: Exception during SidechainCoreTransaction creation expected", exceptionOccurred);
    }


    @Test
    public void semanticValidity() {
        List<byte[]> inputsIds = Arrays.asList(getRandomBoxId(), getRandomBoxId());

        List<NoncedBoxData<Proposition, NoncedBox<Proposition>>> outputsData = new ArrayList<>();
        outputsData.add((NoncedBoxData)getRegularBoxData());
        outputsData.add((NoncedBoxData)getForgerBoxData());
        outputsData.add((NoncedBoxData)getWithdrawalRequestBoxData());

        List<Proof<Proposition>> proofs = new ArrayList<>();
        proofs.add((Proof)getRandomSignature25519());
        proofs.add((Proof)getRandomSignature25519());

        long fee = 100L;
        long timestamp = 13213L;

        SidechainBoxesDataCompanion boxesDataCompanion = new SidechainBoxesDataCompanion(new HashMap<>());
        SidechainProofsCompanion proofsCompanion = new SidechainProofsCompanion(new HashMap<>());


        // Test 1: create semantically valid transaction
        SidechainCoreTransaction tx = new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, timestamp, boxesDataCompanion, proofsCompanion);
        assertTrue("Transaction expected to be semantically Valid.", tx.semanticValidity());


        // Test 2: create semantically invalid transaction - fee is negative
        tx = new SidechainCoreTransaction(inputsIds, outputsData, proofs, -10L, timestamp, boxesDataCompanion, proofsCompanion);
        assertFalse("Transaction expected to be semantically Invalid.", tx.semanticValidity());


        // Test 3: create semantically invalid transaction - timestamp is negative
        tx = new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, -10L, boxesDataCompanion, proofsCompanion);
        assertFalse("Transaction expected to be semantically Invalid.", tx.semanticValidity());


        // Test 4: create semantically invalid transaction - inputs ids list is empty
        tx = new SidechainCoreTransaction(new ArrayList<>(), outputsData, proofs, fee, timestamp, boxesDataCompanion, proofsCompanion);
        assertFalse("Transaction expected to be semantically Invalid.", tx.semanticValidity());


        // Test 5: create semantically invalid transaction - outputs data list is empty
        tx = new SidechainCoreTransaction(inputsIds, new ArrayList<>(), proofs, fee, timestamp, boxesDataCompanion, proofsCompanion);
        assertFalse("Transaction expected to be semantically Invalid.", tx.semanticValidity());


        // Test 6: create semantically invalid transaction - number of inputs (1) is different to number of proofs (2)
        tx = new SidechainCoreTransaction(Arrays.asList(getRandomBoxId()), outputsData, proofs, fee, timestamp, boxesDataCompanion, proofsCompanion);
        assertFalse("Transaction expected to be semantically Invalid.", tx.semanticValidity());


        // Test 7: create semantically invalid transaction - inputs list contains duplicates
        byte[] boxId = getRandomBoxId();
        tx = new SidechainCoreTransaction(Arrays.asList(boxId, boxId), outputsData, proofs, fee, timestamp, boxesDataCompanion, proofsCompanion);
        assertFalse("Transaction expected to be semantically Invalid.", tx.semanticValidity());
    }
}
