package com.horizen.account.transaction;

import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.*;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;

import static org.junit.Assert.*;

public class EthereumTransactionTest {
    EthereumTransaction ethereumTransaction;
    RawTransaction rawTransaction;
    SignedRawTransaction signedRawTransaction;
    SignatureSecp256k1 secp256k1Signature;
    AddressProposition addressProposition;
    final BigInteger someValue = BigInteger.ONE;
    Sign.SignatureData msgSignature;

    @Before
    public void BeforeEachTest() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // Create the raw Transaction
        String payload = "This is string to sign";
        var message = payload.getBytes(StandardCharsets.UTF_8);
        rawTransaction = RawTransaction.createTransaction(someValue,
                someValue, someValue, "0x", someValue, "");

        // Create a key pair, create tx signature and create ethereum Transaction
        ECKeyPair pair = Keys.createEcKeyPair();
        msgSignature = Sign.signMessage(message, pair, true);
        signedRawTransaction = new SignedRawTransaction(someValue,
                someValue, someValue, "0x", someValue, "",
                msgSignature);
        secp256k1Signature = new SignatureSecp256k1(msgSignature);
        addressProposition = new AddressProposition(BytesUtils.fromHexString(Keys.getAddress(pair)));

    }

    @Test
    public void ethereumRawTransactionTest() throws TransactionSemanticValidityException {
        // Test 1: everything is correct
        boolean exceptionOccurred = false;
        try {
            new EthereumTransaction(rawTransaction);
        } catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertFalse("Test1: Successful EthereumTransaction creation expected.", exceptionOccurred);

        // Test 2: raw transaction is null
        exceptionOccurred = false;
        try {
            new EthereumTransaction(null);
        } catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test2: Exception during EthereumTransaction creation expected.", exceptionOccurred);

        // Test 5: toString function returns correctly
        ethereumTransaction = new EthereumTransaction(rawTransaction);
        assertEquals("Ethereum Transaction to String expected to be equal",
                "EthereumTransaction{from=, nonce=0x1, gasPrice=0x1, gasLimit=0x1, to=0x, data=, Signature=}",
                ethereumTransaction.toString());

        // Test 6: ethereum transaction object returns transaction type id correctly
        assertEquals((byte) 2, ethereumTransaction.transactionTypeId());

        // Test 7: ethereum transaction object returns version correctly
        assertEquals(1, ethereumTransaction.version());

        // Test 8: ethereum transaction instance returns messageToSign correctly
        EthereumTransaction ethereumTransactionDeserialize = EthereumTransactionSerializer.getSerializer().parseBytes(ethereumTransaction.bytes());
        assert (Arrays.equals(ethereumTransaction.messageToSign(), ethereumTransactionDeserialize.messageToSign()));

        // Test 9: ethereum transaction instance returns passed RawTransaction correctly
        assertEquals(rawTransaction, ethereumTransaction.getTransaction());

        // Test 10: ethereum transaction instance returns Signature correctly
        assertEquals(null, ethereumTransaction.getSignature());

        // Test 11: ethereum transaction instance returns transaction serializer correctly
        assertEquals(EthereumTransactionSerializer.getSerializer(), ethereumTransaction.serializer());

        // Test 12: ethereum transaction instance returns null as to is empty or has false size
        assertEquals(null, ethereumTransaction.getTo());

        // Test 13: ethereum transaction instance returns to proposition address correctly
        RawTransaction toTx = RawTransaction.createTransaction(someValue,
                someValue, someValue, "00112233445566778899AABBCCDDEEFF01020304", someValue, "");
        EthereumTransaction toEthereumTransaction = new EthereumTransaction(toTx);
        AddressProposition toProposition = new AddressProposition(BytesUtils.fromHexString("00112233445566778899AABBCCDDEEFF01020304"));
        assert (Arrays.equals(toEthereumTransaction.getTo().address(), toProposition.address()));
    }

    @Test
    public void ethereumSignedRawTransactionTest() throws TransactionSemanticValidityException {
        // Test 1: everything is correct
        boolean exceptionOccurred = false;
        try {
            new EthereumTransaction(signedRawTransaction);
        } catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertFalse("Test1: Successful EthereumTransaction creation expected.", exceptionOccurred);

        // Test 2: raw transaction is null
        exceptionOccurred = false;
        try {
            new EthereumTransaction(null);
        } catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test2: Exception during EthereumTransaction creation expected.", exceptionOccurred);

        exceptionOccurred = false;
        try {
            var falseSignedRawTransaction = new SignedRawTransaction(someValue,
                    someValue, someValue, "0x", someValue, "",
                    null);
            new EthereumTransaction(falseSignedRawTransaction);
        } catch (RuntimeException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test3: Exception during EthereumTransaction creation expected", exceptionOccurred);

        // Test 3: toString function returns correctly
        ethereumTransaction = new EthereumTransaction(signedRawTransaction);
        try {
            assertEquals("Ethereum Transaction to String expected to be equal",
                    "EthereumTransaction{from=" + signedRawTransaction.getFrom()
                            + ", nonce=0x1, gasPrice=0x1, gasLimit=0x1, to=0x, data=, "
                            + "Signature=" + secp256k1Signature.toString() + "}",
                    ethereumTransaction.toString());
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }

        // Test 4: ethereum transaction object returns transaction type id correctly
        assertEquals((byte) 2, ethereumTransaction.transactionTypeId());

        // Test 5: ethereum transaction object returns version correctly
        assertEquals(1, ethereumTransaction.version());

        // Test 6: ethereum transaction instance returns messageToSign correctly
        EthereumTransaction ethereumTransactionDeserialize = EthereumTransactionSerializer.getSerializer().parseBytes(ethereumTransaction.bytes());
        assert (Arrays.equals(ethereumTransaction.messageToSign(), ethereumTransactionDeserialize.messageToSign()));

        // Test 7: ethereum transaction instance returns passed RawTransaction correctly
        assertEquals(signedRawTransaction, ethereumTransaction.getTransaction());

        // Test 8: ethereum transaction instance returns Signature correctly
        assert (Arrays.equals(secp256k1Signature.bytes(), ethereumTransaction.getSignature().bytes()));

        // Test 9: ethereum transaction instance returns transaction serializer correctly
        assertEquals(EthereumTransactionSerializer.getSerializer(), ethereumTransaction.serializer());

        // Test 10: ethereum transaction instance returns null as to is empty or has false size
        assertEquals(null, ethereumTransaction.getTo());

        // Test 11: ethereum transaction instance returns to proposition address correctly
        SignedRawTransaction toTx = new SignedRawTransaction(someValue,
                someValue, someValue, "00112233445566778899AABBCCDDEEFF01020304",
                someValue, "", msgSignature);
        EthereumTransaction toEthereumTransaction = new EthereumTransaction(toTx);
        AddressProposition toProposition = new AddressProposition(
                BytesUtils.fromHexString("00112233445566778899AABBCCDDEEFF01020304"));
        assert (Arrays.equals(toEthereumTransaction.getTo().address(), toProposition.address()));
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
