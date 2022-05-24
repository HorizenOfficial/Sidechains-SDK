package com.horizen.account.transaction;

import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.PublicKeySecp256k1Proposition;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import org.bouncycastle.util.Strings;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;

import static org.junit.Assert.*;

public class EthereumTransactionTest {
    EthereumTransaction ethereumTransaction;
    RawTransaction rawTX;
    SignatureSecp256k1 txSignature;
    PublicKeySecp256k1Proposition txProposition;
    final BigInteger someValue = BigInteger.ONE;

    @Before
    public void BeforeEachTest() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // Create the raw Transaction
        String payload = "This is string to sign";
        var message = payload.getBytes(StandardCharsets.UTF_8);
        rawTX = RawTransaction.createTransaction(someValue,
                someValue, someValue, "0x", someValue, "");

        // Create a key pair, create tx signature and create ethereum Transaction
        ECKeyPair pair = Keys.createEcKeyPair();
        var msgSignature = Sign.signMessage(message, pair, true);
        txSignature = new SignatureSecp256k1(msgSignature);
        txProposition = new PublicKeySecp256k1Proposition(Strings.toByteArray("0x" + Keys.getAddress(pair)));
    }

    @Test
    public void ethereumTransactionTest() throws TransactionSemanticValidityException {
        // Test 1: everything is correct
        boolean exceptionOccurred = false;
        try {
            new EthereumTransaction(rawTX, txSignature, txProposition);
        } catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertFalse("Test1: Successful EthereumTransaction creation expected.", exceptionOccurred);

        // Test 2: raw transaction is null
        exceptionOccurred = false;
        try {
            new EthereumTransaction(null, txSignature, txProposition);
        } catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test2: Exception during EthereumTransaction creation expected.", exceptionOccurred);

        // Test 3: signature is null
        exceptionOccurred = false;
        try {
            new EthereumTransaction(rawTX, null, txProposition);
        } catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test3: Exception during EthereumTransaction creation expected.", exceptionOccurred);

        // Test 4: proposition is null
        exceptionOccurred = false;
        try {
            new EthereumTransaction(rawTX, txSignature, null);
        } catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test4: Exception during EthereumTransaction creation expected.", exceptionOccurred);

        // Test 5: toString function returns correctly
        ethereumTransaction = new EthereumTransaction(rawTX, txSignature, txProposition);
        assertEquals("Ethereum Transaction to String expected to be equal",
                "EthereumTransaction{from=" + Strings.fromByteArray(txProposition.address())
                        + ", nonce=0x1, gasPrice=0x1, gasLimit=0x1, to=0x, data=, "
                        + "Signature=" + txSignature.toString() + "}",
                ethereumTransaction.toString());

        // Test 6: ethereum transaction object returns transaction type id correctly
        assertEquals((byte) 2, ethereumTransaction.transactionTypeId());

        // Test 7: ethereum transaction object returns version correctly
        assertEquals(1, ethereumTransaction.version());

        // Test 8: ethereum transaction instance returns messageToSign correctly
        EthereumTransaction ethereumTransactionDeserialize = EthereumTransactionSerializer.getSerializer().parseBytes(ethereumTransaction.bytes());
        assert (Arrays.equals(ethereumTransaction.messageToSign(), ethereumTransactionDeserialize.messageToSign()));

        // Test 9: ethereum transaction instance returns passed RawTransaction correctly
        assertEquals(rawTX, ethereumTransaction.getTransaction());

        // Test 10: ethereum transaction instance returns passed Signature correctly
        assertEquals(txSignature, ethereumTransaction.getSignature());

        // Test 11: ethereum transaction instance returns transaction serializer correctly
        assertEquals(EthereumTransactionSerializer.getSerializer(), ethereumTransaction.serializer());

        // Test 12: ethereum transaction instance returns null as to is empty or has false size
        assertEquals(null, ethereumTransaction.getTo());

        // Test 13: ethereum transaction instance returns to proposition address correctly
        RawTransaction toTx = RawTransaction.createTransaction(someValue,
                someValue, someValue, "0x00112233445566778899AABBCCDDEEFF01020304", someValue, "");
        EthereumTransaction toEthereumTransaction = new EthereumTransaction(toTx, txSignature, txProposition);
        PublicKeySecp256k1Proposition toProposition = new PublicKeySecp256k1Proposition(Strings.toByteArray("0x00112233445566778899AABBCCDDEEFF01020304"));
        assertEquals(Strings.fromByteArray(toEthereumTransaction.getTo().address()), Strings.fromByteArray(toProposition.address()));
    }

    /** TODO: We need a test case - later, because of missing information - for whole ethereum transaction data
     * {
     * "jsonrpc":"2.0",
     * "id":0,
     * "result":{
     *  "blockHash":"0x43dd926aa138a58d3f4740dae387bcff3c7bc525db2d0a449f323f8b8f92a229",
     *  "blockNumber":"0xa4f285",
     *  "from":"0xea8a7ef30f894bce23b42314613458d13f9d43ea",
     *  "gas":"0x30d40",
     *  "gasPrice":"0x2e90edd000",
     *  "hash":"0x72ee43a3784cc6749f64fad1ecf0bbd51a54dd6892ae0573f211566809e0d511",
     *  "input":"0x",
     *  "nonce":"0x1e7",
     *  "to":"0xbd064928cdd4fd67fb99917c880e6560978d7ca1",
     *  "transactionIndex":"0x0",
     *  "value":"0xde0b6b3a7640000",
     *  "v":"0x25",
     *  "r":"0x7e833413ead52b8c538001b12ab5a85bac88db0b34b61251bb0fc81573ca093f",
     *  "s":"0x49634f1e439e3760265888434a2f9782928362412030db1429458ddc9dcee995"
     *  }
     * }
     */
}
