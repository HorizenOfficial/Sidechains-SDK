package com.horizen.transaction;

import com.horizen.box.Box;
import com.horizen.box.data.*;
import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proof.Proof;
import com.horizen.proof.Signature25519;
import com.horizen.proposition.MCPublicKeyHashProposition;
import com.horizen.proposition.Proposition;
import com.horizen.utils.BytesUtils;
import org.junit.Test;
import scala.util.Try;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class SidechainCoreTransactionSerializerTest extends BoxFixtureClass {

    @Test
    public void serializeCoreData() {
        List<byte[]> inputsIds = Arrays.asList(getRandomBoxId());

        List<NoncedBoxData<Proposition, Box<Proposition>>> outputsData = new ArrayList<>();
        outputsData.add((NoncedBoxData) getZenBoxData());
        outputsData.add((NoncedBoxData)getForgerBoxData());
        outputsData.add((NoncedBoxData)getWithdrawalRequestBoxData());

        List<Proof<Proposition>> proofs = new ArrayList<>();
        proofs.add((Proof)getRandomSignature25519());

        long fee = 100L;


        SidechainCoreTransaction transaction = new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, SidechainCoreTransaction.SIDECHAIN_CORE_TRANSACTION_VERSION);
        TransactionSerializer serializer = transaction.serializer();

        // Serializer Core transaction that contains only Core types
        byte[] bytes = serializer.toBytes(transaction);

        Try<SidechainCoreTransaction> t = serializer.parseBytesTry(bytes);
        assertTrue("Transaction deserialization failed.", t.isSuccess());
        assertEquals("Deserialized transactions expected to be equal", transaction.id(), t.get().id());
        assertEquals("Deserialized transactions fee is different to the origin", transaction.fee(), t.get().fee());
        assertArrayEquals("Original and deserialized transactions expected to have equal byte representation.", bytes, serializer.toBytes(t.get()));
    }

    @Test
    public void regressionTest() {
        List<byte[]> inputsIds = Arrays.asList(getRandomBoxId(123L));

        List<NoncedBoxData<Proposition, Box<Proposition>>> outputsData = new ArrayList<>();
        outputsData.add((NoncedBoxData)new ZenBoxData(getPrivateKey25519("1".getBytes()).publicImage(), 100L));
        outputsData.add((NoncedBoxData)new WithdrawalRequestBoxData(new MCPublicKeyHashProposition(BytesUtils.fromHexString("811d42a49dffaee0cb600dee740604b4d5bd0cfb")), 40L));

        List<Proof<Proposition>> proofs = new ArrayList<>();
        proofs.add((Proof)new Signature25519(BytesUtils.fromHexString("34098ab081a042cb9a4da5faf05c9d1b970cf365a776acd356e980313335ac55eb41d80a6aa816e311cd1ed488b18ef8a10f278b4de19a5b5865a16f6e5bb001")));

        long fee = 100L;

        SidechainCoreTransaction transaction = new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, SidechainCoreTransaction.SIDECHAIN_CORE_TRANSACTION_VERSION);
        // Set `true` and run if you want to update regression data.
        if(false) {
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter("src/test/resources/sidechaincoretransaction_hex"));
                out.write(BytesUtils.toHexString(transaction.bytes()));
                out.close();
            } catch (Throwable e) {
            }
        }
        
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
    }
}
