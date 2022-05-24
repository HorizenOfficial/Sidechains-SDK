package com.horizen.account.transaction;

import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.PublicKeySecp256k1Proposition;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import static org.junit.Assert.*;

public class EthereumTransactionTest {
    EthereumTransaction ethereumTransaction;
    RawTransaction rawTX;
    SignatureSecp256k1 txSignature;
    PublicKeySecp256k1Proposition txProposition;

    @Before
    public void BeforeEachTest() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // Create the raw Transaction
        String payload = "This is string to sign";
        var message = payload.getBytes(StandardCharsets.UTF_8);
        var someValue = BigInteger.ONE;
        rawTX = RawTransaction.createTransaction(someValue,
                someValue, someValue, "0x", someValue, "");

        // Create a key pair, create tx signature and create ethereum Transaction
        ECKeyPair pair = Keys.createEcKeyPair();
        var msgSignature = Sign.signMessage(message, pair, true);
        txSignature = new SignatureSecp256k1(msgSignature);
        txProposition = new PublicKeySecp256k1Proposition(Numeric.hexStringToByteArray(Keys.getAddress(pair)));
    }

    @Test
    public void ethereumTransactionTest() {
        // Test 1: everything is correct
        boolean exceptionOccurred = false;
        try {
            new EthereumTransaction(rawTX, txSignature, txProposition);
        }
        catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertFalse("Test1: Successful EthereumTransaction creation expected.", exceptionOccurred);

        // Test 2: raw transaction is null
        exceptionOccurred = false;
        try {
            new EthereumTransaction(null, txSignature, txProposition);
        }
        catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test2: Exception during EthereumTransaction creation expected.", exceptionOccurred);

        // Test 3: signature is null
        exceptionOccurred = false;
        try {
            new EthereumTransaction(rawTX, null, txProposition);
        }
        catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test3: Exception during EthereumTransaction creation expected.", exceptionOccurred);

        // Test 4: proposition is null
        exceptionOccurred = false;
        try {
            new EthereumTransaction(rawTX, txSignature, null);
        }
        catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test4: Exception during EthereumTransaction creation expected.", exceptionOccurred);

        // Test 5: toString function returns correctly
        ethereumTransaction = new EthereumTransaction(rawTX, txSignature, txProposition);
        assertEquals("Ethereum Transaction to String expected to be equal", ethereumTransaction.toString(),
                "EthereumTransaction{nonce=0x1, gasPrice=0x1, gasLimit=0x1, to=0x, data=, " +
                        "Signature="+txSignature.toString()+"}");

        // Test 6: ethereum transaction object returns transaction type id correctly
        assertEquals(ethereumTransaction.transactionTypeId(), (byte)2);

        // Test 7: ethereum transaction object returns version correctly
        assertEquals(ethereumTransaction.version(), 1);

        // Test 8: ethereum transaction instance returns messageToSign correctly
        // currently not that interesting
        assertNotNull(ethereumTransaction.messageToSign());

        // Test 9: ethereum transaction instance returns passed RawTransaction correctly
        assertEquals(ethereumTransaction.getTransaction(), rawTX);

        // Test 10: ethereum transaction instance returns passed Signature correctly
        assertEquals(ethereumTransaction.getSignature(), txSignature);

        // Test 11: ethereum transaction instance returns transaction serializer correctly
        assertEquals(ethereumTransaction.serializer(), EthereumTransactionSerializer.getSerializer());
    }
}
