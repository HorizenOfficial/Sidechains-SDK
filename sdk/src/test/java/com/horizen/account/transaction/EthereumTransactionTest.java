package com.horizen.account.transaction;

import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;

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
    public void ethereumLegacyRawTransactionTest() {
        // Test 1: direct constructor test
        try {
            var someTx = new EthereumTransaction(
                    "0x", someValue, someValue, someValue,
                    someValue, "", null
            );
            assertNull(someTx.getSignature());
        } catch (NullPointerException e) {
            fail("Test1: Successful EthereumTransaction creation expected.");
        }


        // Test 2: raw tx constructor check
        try {
            new EthereumTransaction(rawTransaction);
        } catch (NullPointerException e) {
            fail("Test2: Successful EthereumTransaction creation expected.");
        }

        // Test 2: raw transaction is null
        assertThrows("Test3: Exception during EthereumTransaction creation expected.", NullPointerException.class,
                () -> new EthereumTransaction(null));

        // Test 4: toString function returns correctly
        ethereumTransaction = new EthereumTransaction(rawTransaction);
        assertEquals("Ethereum Transaction to String expected to be equal",
                "EthereumTransaction{from=, nonce=0x1, gasPrice=0x1, gasLimit=0x1, to=0x, value=0x1, data=0x, " +
                        "Signature=}",
                ethereumTransaction.toString());

        // Test 5: ethereum transaction object returns transaction type id correctly
        assertEquals((byte) 2, ethereumTransaction.transactionTypeId());

        // Test 6: ethereum transaction object returns version correctly
        assertEquals(0/*legacy*/, ethereumTransaction.version());

        // Test 7: ethereum transaction instance returns messageToSign correctly
        EthereumTransaction ethereumTransactionDeserialize = EthereumTransactionSerializer.getSerializer().parseBytes(ethereumTransaction.bytes());
        assertArrayEquals(ethereumTransaction.messageToSign(), ethereumTransactionDeserialize.messageToSign());

        // Test 7: getMaxFeePerGas should be null, as this is a legacy transaction
        assertNull(ethereumTransaction.getMaxFeePerGas());
        // Test 8: getMaxPriorityFeePerGas should be null, as this is a legacy transaction
        assertNull(ethereumTransaction.getMaxPriorityFeePerGas());
        // Test 9: getChainId should be null, as this is a legacy transaction
        assertNull(ethereumTransaction.getChainId());
        // Test 10: getFrom should be null, as this is an unsigned tx
        assertEquals("", ethereumTransaction.getFromAddress());

        // Test 11: ethereum transaction instance returns Signature correctly
        assertNull(ethereumTransaction.getSignature());

        // Test 12: ethereum transaction instance returns transaction serializer correctly
        assertEquals(EthereumTransactionSerializer.getSerializer(), ethereumTransaction.serializer());

        // Test 13: ethereum transaction instance returns null as to is empty or has false size
        assertNull(ethereumTransaction.getTo());

        // Test 14: ethereum transaction instance returns to proposition address correctly
        RawTransaction toTx = RawTransaction.createTransaction(someValue,
                someValue, someValue, "00112233445566778899AABBCCDDEEFF01020304", someValue, "");
        EthereumTransaction toEthereumTransaction = new EthereumTransaction(toTx);
        AddressProposition toProposition = new AddressProposition(BytesUtils.fromHexString("00112233445566778899AABBCCDDEEFF01020304"));
        assertArrayEquals(toEthereumTransaction.getTo().address(), toProposition.address());

        // Test 15: ethereum transaction made from an unsigned raw transaction should return false for isSigned
        assertFalse(ethereumTransaction.isSigned());

        // Test 16: ethereum transaction made from an unsigned raw legacy transaction should return false for isEIP1559
        assertFalse(ethereumTransaction.isEIP1559());

        var semanticallyInvalidTransaction = new EthereumTransaction("", someValue, someValue, BigInteger.ZERO,
                someValue, "",
                null);
        assertThrows("Semantic validity should have failed", TransactionSemanticValidityException.class,
                semanticallyInvalidTransaction::semanticValidity);
    }

    @Test
    public void ethereumEIP1559RawTransactionTest() {
        // Test 0: direct constructor
        var someTx = new EthereumTransaction(someValue.longValue(),
                "0x", someValue, someValue, someValue, someValue,
                someValue, "", null
        );
        assertNull(someTx.getSignature());

        rawTransaction = RawTransaction.createTransaction(someValue.longValue(), someValue,
                someValue, "0x", someValue, "", someValue, someValue);

        // Test 1: everything is correct
        boolean exceptionOccurred = false;
        try {
            new EthereumTransaction(rawTransaction);
        } catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertFalse("Test1: Successful EthereumTransaction creation expected.", exceptionOccurred);

        // Test 2: toString function returns correctly
        ethereumTransaction = new EthereumTransaction(rawTransaction);
        assertEquals("EthereumTransaction{from=, nonce=0x1, gasLimit=0x1, to=0x, value=0x1, data=0x, " +
                        "maxFeePerGas=0x1, maxPriorityFeePerGas=0x1, Signature=}",
                ethereumTransaction.toString());

        // Test 3: ethereum transaction object returns transaction type id correctly
        assertEquals((byte) 2, ethereumTransaction.transactionTypeId());

        // Test 4: ethereum transaction object returns version correctly
        assertEquals(2 /*eip1559*/, ethereumTransaction.version());

        assertNull(ethereumTransaction.getGasPrice());

        // Test 5: ethereum transaction instance returns messageToSign correctly
        EthereumTransaction ethereumTransactionDeserialize = EthereumTransactionSerializer.getSerializer().parseBytes(ethereumTransaction.bytes());
        assertArrayEquals(ethereumTransaction.messageToSign(), ethereumTransactionDeserialize.messageToSign());

        // Test 6: ethereum transaction instance returns Signature correctly
        assertNull(ethereumTransaction.getSignature());

        // Test 7: ethereum transaction instance returns transaction serializer correctly
        assertEquals(EthereumTransactionSerializer.getSerializer(), ethereumTransaction.serializer());

        // Test 8: ethereum transaction instance returns null as to is empty or has false size
        assertNull(ethereumTransaction.getTo());

        assertEquals(ethereumTransaction.getMaxFeePerGas(), someValue);
        assertEquals(ethereumTransaction.getMaxPriorityFeePerGas(), someValue);
        assertEquals(ethereumTransaction.getChainId().longValue(), someValue.longValue());

        // Test 9: ethereum transaction instance returns to proposition address correctly
        RawTransaction toTx =
                RawTransaction.createTransaction(someValue.longValue(), someValue,
                        someValue, "0x00112233445566778899AABBCCDDEEFF01020304", someValue,
                        "", someValue, someValue);
        EthereumTransaction toEthereumTransaction = new EthereumTransaction(toTx);
        AddressProposition toProposition = new AddressProposition(BytesUtils.fromHexString("00112233445566778899AABBCCDDEEFF01020304"));
        assertArrayEquals(toEthereumTransaction.getTo().address(), toProposition.address());

        // Test 10: ethereum transaction made from an unsigned raw transaction should return false for isSigned
        assertFalse(ethereumTransaction.isSigned());

        // Test 11: ethereum transaction made from an unsigned raw legacy transaction should return false for isEIP1559
        assertTrue(ethereumTransaction.isEIP1559());
    }

    @Test
    public void ethereumSignedRawTransactionTest() {
        // Test 0: direct constructor
        try {
            var someTx = new EthereumTransaction(
                    "0x", someValue, someValue, someValue,
                    someValue, "", signedRawTransaction.getSignatureData()
            );
            assertNotNull(someTx.getSignature());
        } catch (Exception ignored) {
            fail("Should not happen");
        }

        // Test 1: everything is correct
        boolean exceptionOccurred = false;
        try {
            new EthereumTransaction(signedRawTransaction);
        } catch (NullPointerException e) {
            fail("Test1: Successful EthereumTransaction creation expected.");
        }

        // Test 2: raw transaction is null
        assertThrows("Test2: Exception during EthereumTransaction creation expected.",
                NullPointerException.class, () -> new EthereumTransaction(null));


        var falseSignedRawTransaction = new SignedRawTransaction(someValue,
                someValue, someValue, "0x", someValue, "",
                null);

        assertThrows("Test3: Exception during EthereumTransaction creation expected",
                RuntimeException.class, () -> new EthereumTransaction(falseSignedRawTransaction));

        // Test 3: toString function returns correctly
        ethereumTransaction = new EthereumTransaction(signedRawTransaction);
        try {
            assertEquals("Ethereum Transaction to String expected to be equal",
                    "EthereumTransaction{from=" + signedRawTransaction.getFrom()
                            + ", nonce=0x1, gasPrice=0x1, gasLimit=0x1, to=0x, value=0x1, data=0x, "
                            + "Signature=" + secp256k1Signature.toString() + "}",
                    ethereumTransaction.toString());
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }

        assertThrows("Semantic validity should fail", TransactionSemanticValidityException.class,
                ethereumTransaction::semanticValidity);

        // Test 4: ethereum transaction object returns transaction type id correctly
        assertEquals((byte) 2, ethereumTransaction.transactionTypeId());

        // Test 5: ethereum transaction object returns version correctly
        assertEquals(0 /*legacy*/, ethereumTransaction.version());

        // Test 6: ethereum transaction instance returns messageToSign correctly
        assertArrayEquals(ethereumTransaction.messageToSign(),
                TransactionEncoder.encode(ethereumTransaction.getTransaction()));

        // Test 7: ethereum transaction instance returns Signature correctly
        assertArrayEquals(secp256k1Signature.bytes(), ethereumTransaction.getSignature().bytes());

        // Test 8: ethereum transaction instance returns transaction serializer correctly
        assertEquals(EthereumTransactionSerializer.getSerializer(), ethereumTransaction.serializer());

        // Test 9: ethereum transaction instance returns null as to is empty or has false size
        assertNull(ethereumTransaction.getTo());

        // Test 10: ethereum transaction instance returns to proposition address correctly
        SignedRawTransaction toTx = new SignedRawTransaction(someValue,
                someValue, someValue, "00112233445566778899AABBCCDDEEFF01020304",
                someValue, "", msgSignature);
        EthereumTransaction toEthereumTransaction = new EthereumTransaction(toTx);
        AddressProposition toProposition = new AddressProposition(
                BytesUtils.fromHexString("00112233445566778899AABBCCDDEEFF01020304"));
        assertArrayEquals(toEthereumTransaction.getTo().address(), toProposition.address());
    }

    @Test
    public void ethereumSignedEIP1559TransactionTest() {
        // Test 0: direct constructor
        try {
            var someTx = new EthereumTransaction(someValue.longValue(),
                    "0x", someValue, someValue, someValue, someValue,
                    someValue, "", signedRawTransaction.getSignatureData()
            );
            assertNotNull(someTx.getSignature());
            assertEquals("EthereumTransaction{from=" + someTx.getFromAddress() + ", nonce=0x1, gasLimit=0x1, to=0x, value=" +
                            "0x1, data=0x, " +
                            "maxFeePerGas=0x1, maxPriorityFeePerGas=0x1, Signature=" + someTx.getSignature().toString() + '}',
                    someTx.toString());

            assertEquals(
                    Numeric.toHexString(someTx.getFrom().address()),
                    someTx.getFromAddress()
            );
        } catch (Exception ignored) {
            fail("Should not happen");
        }

        // Test 1: everything is correct
        boolean exceptionOccurred = false;
        try {
            new EthereumTransaction(signedRawTransaction);
        } catch (NullPointerException e) {
            exceptionOccurred = true;
        }
        assertFalse("Test1: Successful EthereumTransaction creation expected.", exceptionOccurred);

        // Test 2: raw transaction is null
        assertThrows("Test2: Exception during EthereumTransaction creation expected.",
                NullPointerException.class, () -> new EthereumTransaction(null));

        var falseSignedRawTransaction = new SignedRawTransaction(someValue,
                someValue, someValue, "0x", someValue, "",
                null);
        assertThrows("Test3: Exception during EthereumTransaction creation expected",
                RuntimeException.class, () -> new EthereumTransaction(falseSignedRawTransaction));

        // Test 3: toString function returns correctly
        ethereumTransaction = new EthereumTransaction(signedRawTransaction);
        try {
            assertEquals("Ethereum Transaction to String expected to be equal",
                    "EthereumTransaction{from=" + signedRawTransaction.getFrom()
                            + ", nonce=0x1, gasPrice=0x1, gasLimit=0x1, to=0x, value=0x1, data=0x, "
                            + "Signature=" + secp256k1Signature.toString() + "}",
                    ethereumTransaction.toString());
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }

        // Test 4: ethereum transaction object returns transaction type id correctly
        assertEquals((byte) 2, ethereumTransaction.transactionTypeId());

        // Test 5: ethereum transaction object returns version correctly
        assertEquals(0 /*legacy*/, ethereumTransaction.version());

        // Test 6: ethereum transaction instance returns messageToSign correctly
        assertArrayEquals(ethereumTransaction.messageToSign(),
                TransactionEncoder.encode(ethereumTransaction.getTransaction()));

        // Test 7: ethereum transaction instance returns Signature correctly
        assertArrayEquals(secp256k1Signature.bytes(), ethereumTransaction.getSignature().bytes());

        // Test 8: ethereum transaction instance returns transaction serializer correctly
        assertEquals(EthereumTransactionSerializer.getSerializer(), ethereumTransaction.serializer());

        // Test 9: ethereum transaction instance returns null as to is empty or has false size
        assertNull(ethereumTransaction.getTo());

        // Test 10: ethereum transaction instance returns to proposition address correctly
        SignedRawTransaction toTx = new SignedRawTransaction(someValue,
                someValue, someValue, "00112233445566778899AABBCCDDEEFF01020304",
                someValue, "", msgSignature);
        EthereumTransaction toEthereumTransaction = new EthereumTransaction(toTx);
        AddressProposition toProposition = new AddressProposition(
                BytesUtils.fromHexString("00112233445566778899AABBCCDDEEFF01020304"));
        assertArrayEquals(toEthereumTransaction.getTo().address(), toProposition.address());
    }


}
