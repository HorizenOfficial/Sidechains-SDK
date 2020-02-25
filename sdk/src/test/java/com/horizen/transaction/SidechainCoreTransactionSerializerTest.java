package com.horizen.transaction;

import com.horizen.box.NoncedBox;
import com.horizen.box.data.*;
import com.horizen.companion.SidechainBoxesDataCompanion;
import com.horizen.companion.SidechainProofsCompanion;
import com.horizen.customtypes.CustomBoxData;
import com.horizen.customtypes.CustomBoxDataSerializer;
import com.horizen.customtypes.CustomProof;
import com.horizen.customtypes.CustomProofSerializer;
import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proof.Proof;
import com.horizen.proof.ProofSerializer;
import com.horizen.proof.Signature25519;
import com.horizen.proposition.MCPublicKeyHashProposition;
import com.horizen.proposition.Proposition;
import com.horizen.utils.BytesUtils;
import org.junit.Test;
import scala.util.Try;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

public class SidechainCoreTransactionSerializerTest extends BoxFixtureClass {

    @Test
    public void serializeCoreData() {
        List<byte[]> inputsIds = Arrays.asList(getRandomBoxId());

        List<NoncedBoxData<Proposition, NoncedBox<Proposition>>> outputsData = new ArrayList<>();
        outputsData.add((NoncedBoxData)getRegularBoxData());
        outputsData.add((NoncedBoxData)getForgerBoxData());
        outputsData.add((NoncedBoxData)getCertifierRightBoxData());
        outputsData.add((NoncedBoxData)getWithdrawalRequestBoxData());

        List<Proof<Proposition>> proofs = new ArrayList<>();
        proofs.add((Proof)getRandomSignature25519());

        long fee = 100L;
        long timestamp = 13213L;

        SidechainBoxesDataCompanion boxesDataCompanion = new SidechainBoxesDataCompanion(new HashMap<>());
        SidechainProofsCompanion proofsCompanion = new SidechainProofsCompanion(new HashMap<>());


        SidechainCoreTransaction transaction = new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, timestamp, boxesDataCompanion, proofsCompanion);
        TransactionSerializer serializer = transaction.serializer();

        // Serializer Core transaction that contains only Core types
        byte[] bytes = serializer.toBytes(transaction);

        Try<SidechainCoreTransaction> t = serializer.parseBytesTry(bytes);
        assertTrue("Transaction deserialization failed.", t.isSuccess());
        assertEquals("Deserialized transactions expected to be equal", transaction.id(), t.get().id());
        assertEquals("Deserialized transactions fee is different to the origin", transaction.fee(), t.get().fee());
        assertEquals("Deserialized transactions timestamp is different to the origin", transaction.timestamp(), t.get().timestamp());
        assertArrayEquals("Original and deserialized transactions expected to have equal byte representation.", bytes, serializer.toBytes(t.get()));
    }


    @Test
    public void serializeCustomBoxData() {
        List<byte[]> inputsIds = Arrays.asList(getRandomBoxId());

        List<NoncedBoxData<Proposition, NoncedBox<Proposition>>> outputsData = new ArrayList<>();
        outputsData.add((NoncedBoxData)getRegularBoxData());
        // Add custom box data
        outputsData.add((NoncedBoxData)getCustomBoxData());

        List<Proof<Proposition>> proofs = new ArrayList<>();
        proofs.add((Proof)getRandomSignature25519());

        long fee = 100L;
        long timestamp = 13213L;

        SidechainProofsCompanion proofsCompanion = new SidechainProofsCompanion(new HashMap<>());


        // Test 1: create transaction with CustomBoxData, but WITHOUT support of CustomBox serializer. Serialization expected to be fail.
        SidechainBoxesDataCompanion boxesDataCompanionWithoutCustomSerializer = new SidechainBoxesDataCompanion(new HashMap<>());

        SidechainCoreTransaction transaction = new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, timestamp, boxesDataCompanionWithoutCustomSerializer, proofsCompanion);
        TransactionSerializer serializerWithCoreOnly = transaction.serializer();

        // Serialize Core transaction that contains only Core types serializers
        boolean exceptionOccurred = false;
        try {
            byte[] bytes = serializerWithCoreOnly.toBytes(transaction);
        } catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertTrue("Exception during SidechainCoreTransaction with Custom BoxData and missed serializer expected.", exceptionOccurred);


        // Test 2: create transaction with CustomBoxData and WITH support of CustomBox serializer. Serialization expected to be successful.
        HashMap<Byte, NoncedBoxDataSerializer<NoncedBoxData<Proposition, NoncedBox<Proposition>>>> customBoxDataSerializers = new HashMap<>();
        customBoxDataSerializers.put(CustomBoxData.DATA_TYPE_ID, (NoncedBoxDataSerializer)CustomBoxDataSerializer.getSerializer());
        SidechainBoxesDataCompanion boxesDataCompanionWithCustomSerializer = new SidechainBoxesDataCompanion(customBoxDataSerializers);

        transaction = new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, timestamp, boxesDataCompanionWithCustomSerializer, proofsCompanion);
        TransactionSerializer serializerWithCustom = transaction.serializer();

        byte[] bytes = serializerWithCustom.toBytes(transaction);
        Try<SidechainCoreTransaction> t = serializerWithCustom.parseBytesTry(bytes);
        assertTrue("Transaction deserialization failed.", t.isSuccess());
        assertEquals("Deserialized transactions expected to be equal", transaction.id(), t.get().id());
        assertEquals("Deserialized transactions fee is different to the origin", transaction.fee(), t.get().fee());
        assertEquals("Deserialized transactions timestamp is different to the origin", transaction.timestamp(), t.get().timestamp());
        assertArrayEquals("Original and deserialized transactions expected to have equal byte representation.", bytes, serializerWithCustom.toBytes(t.get()));


        // Test 3: deserialize bytes with CustomBoxData entry using Serializer WITHOUT proper custom serializer - exception expected
        t = serializerWithCoreOnly.parseBytesTry(bytes);
        assertTrue("Transaction deserialization failed.", t.isFailure());
    }


    @Test
    public void serializeCustomProofs() {
        List<byte[]> inputsIds = Arrays.asList(getRandomBoxId(), getRandomBoxId());

        List<NoncedBoxData<Proposition, NoncedBox<Proposition>>> outputsData = new ArrayList<>();
        outputsData.add((NoncedBoxData)getRegularBoxData());

        List<Proof<Proposition>> proofs = new ArrayList<>();
        proofs.add((Proof)getRandomSignature25519());
        // Add CustomProof entry
        proofs.add(getRandomCustomProof());

        long fee = 100L;
        long timestamp = 13213L;

        SidechainBoxesDataCompanion boxesDataCompanion = new SidechainBoxesDataCompanion(new HashMap<>());


        // Test 1: create transaction with CustomProof, but WITHOUT support of CustomProof serializer. Serialization expected to be fail.
        SidechainProofsCompanion proofsCompanionWithoutCustomSerializer = new SidechainProofsCompanion(new HashMap<>());

        SidechainCoreTransaction transaction = new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, timestamp, boxesDataCompanion, proofsCompanionWithoutCustomSerializer);
        TransactionSerializer serializerWithCoreOnly = transaction.serializer();

        // Serialize Core transaction that contains only Core types serializers
        boolean exceptionOccurred = false;
        try {
            byte[] bytes = serializerWithCoreOnly.toBytes(transaction);
        } catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertTrue("Exception during SidechainCoreTransaction with Custom Proof and missed serializer expected.", exceptionOccurred);


        // Test 2: create transaction with CustomProof and WITH support of CustomProof serializer. Serialization expected to be successful.
        HashMap<Byte, ProofSerializer<Proof<Proposition>>> customProofSerializers = new HashMap<>();
        customProofSerializers.put(CustomProof.PROOF_TYPE_ID, (ProofSerializer)CustomProofSerializer.getSerializer());
        SidechainProofsCompanion proofsCompanionWithCustomSerializer = new SidechainProofsCompanion(customProofSerializers);

        transaction = new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, timestamp, boxesDataCompanion, proofsCompanionWithCustomSerializer);
        TransactionSerializer serializerWithCustom = transaction.serializer();

        byte[] bytes = serializerWithCustom.toBytes(transaction);
        Try<SidechainCoreTransaction> t = serializerWithCustom.parseBytesTry(bytes);
        assertTrue("Transaction deserialization failed.", t.isSuccess());
        assertEquals("Deserialized transactions expected to be equal", transaction.id(), t.get().id());
        assertEquals("Deserialized transactions fee is different to the origin", transaction.fee(), t.get().fee());
        assertEquals("Deserialized transactions timestamp is different to the origin", transaction.timestamp(), t.get().timestamp());
        assertArrayEquals("Original and deserialized transactions expected to have equal byte representation.", bytes, serializerWithCustom.toBytes(t.get()));


        // Test 3: deserialize bytes with CustomBoxData entry using Serializer WITHOUT proper custom serializer - exception expected
        t = serializerWithCoreOnly.parseBytesTry(bytes);
        assertTrue("Transaction deserialization failed.", t.isFailure());
    }


    @Test
    public void regressionTest() {
        List<byte[]> inputsIds = Arrays.asList(getRandomBoxId(123L));

        List<NoncedBoxData<Proposition, NoncedBox<Proposition>>> outputsData = new ArrayList<>();
        outputsData.add((NoncedBoxData)new RegularBoxData(getPrivateKey25519("1".getBytes()).publicImage(), 100L));
        outputsData.add((NoncedBoxData)new CertifierRightBoxData(getPrivateKey25519("2".getBytes()).publicImage(), 200L, 10L));
        outputsData.add((NoncedBoxData)new WithdrawalRequestBoxData(new MCPublicKeyHashProposition(BytesUtils.fromHexString("811d42a49dffaee0cb600dee740604b4d5bd0cfb")), 40L));

        List<Proof<Proposition>> proofs = new ArrayList<>();
        proofs.add((Proof)new Signature25519(BytesUtils.fromHexString("34098ab081a042cb9a4da5faf05c9d1b970cf365a776acd356e980313335ac55eb41d80a6aa816e311cd1ed488b18ef8a10f278b4de19a5b5865a16f6e5bb001")));

        long fee = 100L;
        long timestamp = 13213L;

        SidechainBoxesDataCompanion boxesDataCompanion = new SidechainBoxesDataCompanion(new HashMap<>());
        SidechainProofsCompanion proofsCompanion = new SidechainProofsCompanion(new HashMap<>());


        SidechainCoreTransaction transaction = new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, timestamp, boxesDataCompanion, proofsCompanion);
        // Uncomment and run if you want to update regression data.
        /*
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter("src/test/resources/sidechaincoretransaction_hex"));
            out.write(BytesUtils.toHexString(transaction.bytes()));
            out.close();
        } catch (Throwable e) {
        }
        */
        
        byte[] bytes;
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            FileReader file = new FileReader(classLoader.getResource("sidechaincoretransaction_hex").getFile());
            bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
        }
        catch (Exception e) {
            fail(e.toString());
            return;
        }


        TransactionSerializer serializer = transaction.serializer();
        Try<SidechainCoreTransaction> t = serializer.parseBytesTry(bytes);
        assertTrue("Transaction serialization failed.", t.isSuccess());

        SidechainCoreTransaction parsedTransaction = t.get();
        assertEquals("Transaction is different to the origin.", transaction.id(), parsedTransaction.id());
        assertEquals("Deserialized transactions fee is different to the origin", transaction.fee(), parsedTransaction.fee());
        assertEquals("Deserialized transactions timestamp is different to the origin", transaction.timestamp(), parsedTransaction.timestamp());
    }
}
