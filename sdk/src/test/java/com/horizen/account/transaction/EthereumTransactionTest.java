package com.horizen.account.transaction;

import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.evm.TrieHasher;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Map;

import static java.util.Map.entry;
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

    public void checkEthTx(EthereumTransaction tx) {
        String strBefore = tx.toString();
        byte[] encodedTx = EthereumTransactionSerializer.getSerializer().toBytes(tx);
        // read what *it wrote
        EthereumTransaction decodedTx = EthereumTransactionSerializer.getSerializer().parseBytes(encodedTx);
        String strAfter = decodedTx.toString();
        // we must have the same representation
        assertEquals(strBefore, strAfter);
        assertEquals(tx, decodedTx);
    }

    @Test
    public void transactionIdTests() {
        // EIP-1559
        var eipSignedTx = new EthereumTransaction(
                31337,
                "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
                BigInteger.valueOf(0L),
                BigInteger.valueOf(1),
                BigInteger.valueOf(1),
                BigInteger.valueOf(1),
                BigInteger.valueOf(1),
                "",
                new Sign.SignatureData((byte) 27,
                        BytesUtils.fromHexString("805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023"),
                        BytesUtils.fromHexString("568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d"))
        );
        assertEquals("0x0dbd564dfb0c029e471d39a325d0b00bf9686f61f97f200e0b7299117eed51a8", "0x" + eipSignedTx.id());
        checkEthTx(eipSignedTx);

        // Legacy
        var legacyTx = new EthereumTransaction(
                "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
                BigInteger.valueOf(0L),
                BigInteger.valueOf(1),
                BigInteger.valueOf(1),
                BigInteger.valueOf(1),
                "",
                new Sign.SignatureData((byte) 28,
                        BytesUtils.fromHexString("2a4afbdd7e8d99c3df9dfd9e4ecd0afe018d8dec0b8b5fe1a44d5f30e7d0a5c5"),
                        BytesUtils.fromHexString("7ca554a8317ff86eb6b23d06fa210d23e551bed58f58f803a87e5950aa47a9e9"))
        );
        assertEquals("0x306f23ca4948f7b791768878eb540915d0e12bae54c0f7f2a119095de074dab6", "0x" + legacyTx.id());
        checkEthTx(legacyTx);

        // EIP-155 tx
        var eip155Tx = new EthereumTransaction(
                "0x3535353535353535353535353535353535353535",
                BigInteger.valueOf(9L),
                BigInteger.valueOf(20).multiply(BigInteger.TEN.pow(9)),
                BigInteger.valueOf(21000),
                BigInteger.TEN.pow(18),
                "",
                new Sign.SignatureData((byte) 37,
                        BytesUtils.fromHexString("28EF61340BD939BC2195FE537567866003E1A15D3C71FF63E1590620AA636276"),
                        BytesUtils.fromHexString("67CBE9D8997F761AECB703304B3800CCF555C9F3DC64214B297FB1966A3B6D83"))
        );
        assertEquals(Hash.sha3("0xf86c098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a76400008025a028ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276a067cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d83"), "0x" + eip155Tx.id());
        checkEthTx(eip155Tx);

    }

    @Test
    public void ethereumLegacyRawTransactionTest() {
        // Test 1: direct constructor test
        try {
            var someTx = new EthereumTransaction(
                    "0x", someValue, someValue, someValue,
                    someValue, "", null
            );
            assertNull(someTx.getRealSignature());
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
        assertNull(ethereumTransaction.getRealSignature());

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
    public void ethereumLegacyEIP155TransactionTest() {
        // Test 1: direct constructor test
        try {
            Long chainId = Long.valueOf(1);
            var someTx = new EthereumTransaction(
                    "0x3535353535353535353535353535353535353535",
                    BigInteger.valueOf(9),
                    BigInteger.valueOf(20).multiply(BigInteger.TEN.pow(9)),
                    BigInteger.valueOf(21000),
                    BigInteger.TEN.pow(18),
                    "",
                    new Sign.SignatureData(new byte[]{1}, new byte[]{0}, new byte[]{0})
            );
            assertEquals("Chainid was not correct", someTx.getChainId(), chainId);
            assertEquals("EIP-155 message to sign is incorrect", "0x" + BytesUtils.toHexString(someTx.messageToSign()), "0xec098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a764000080018080");
        } catch (NullPointerException e) {
            fail("Test1: Successful EthereumTransaction creation expected.");
        }

        // metamask eip155 tx:
        // - from address: 0x892278d9f50a1da5b2e98e5056f165b1b2486d97
        // - to address: 0xd830603264bd3118cf95f1fc623749337342f9e9
        // - value: 3000000000000000000
        // - chain id: 1997
        String metamaskHexStr = "de01f86d02843b9aca0082520894d830603264bd3118cf95f1fc623749337342f9e98829a2241af62c000080820fbea0f638802002d7c0a3115716f7d24d646452d598050ffc2d6892ba0ed88aeb76bea01dd5a7fb9ea0ec2a414dbb93b09b3e716a3cd05c1e333d40622c63d0c34e3d35";

        EthereumTransaction decodedTx = EthereumTransactionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(metamaskHexStr));
        long chainId = decodedTx.getChainId();
        byte[] fromAddress = decodedTx.getFrom().address();
        byte[] toAddress = decodedTx.getTo().address();

        assertEquals("892278d9f50a1da5b2e98e5056f165b1b2486d97", BytesUtils.toHexString(fromAddress));
        assertEquals("d830603264bd3118cf95f1fc623749337342f9e9", BytesUtils.toHexString(toAddress));
        assertEquals(1997, chainId);
        assertEquals("3000000000000000000", decodedTx.getValue().toString());

        // re-encode and check it is the same
        byte[] encodedTx = EthereumTransactionSerializer.getSerializer().toBytes(decodedTx);
        assertEquals(metamaskHexStr, BytesUtils.toHexString(encodedTx));
    }

    @Test
    public void ethereumEIP1559RawTransactionTest() {
        // Test 0: direct constructor
        var someTx = new EthereumTransaction(someValue.longValue(),
                "0x", someValue, someValue, someValue, someValue,
                someValue, "", null
        );
        assertNull(someTx.getRealSignature());

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
        assertNull(ethereumTransaction.getRealSignature());

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
            assertNotNull(someTx.getRealSignature());
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

        /* semantic validity now works
        assertThrows("Semantic validity should fail", TransactionSemanticValidityException.class,
                ethereumTransaction::semanticValidity);
         */

        // Test 4: ethereum transaction object returns transaction type id correctly
        assertEquals((byte) 2, ethereumTransaction.transactionTypeId());

        // Test 5: ethereum transaction object returns version correctly
        assertEquals(0 /*legacy*/, ethereumTransaction.version());

        // Test 6: ethereum transaction instance returns messageToSign correctly
        assertArrayEquals(ethereumTransaction.messageToSign(),
                TransactionEncoder.encode(ethereumTransaction.getTransaction()));

        // Test 7: ethereum transaction instance returns Signature correctly
        assertArrayEquals(secp256k1Signature.bytes(), ethereumTransaction.getRealSignature().bytes());

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
            assertNotNull(someTx.getRealSignature());
            assertEquals("EthereumTransaction{from=" + someTx.getFromAddress() + ", nonce=0x1, gasLimit=0x1, to=0x, value=" +
                            "0x1, data=0x, " +
                            "maxFeePerGas=0x1, maxPriorityFeePerGas=0x1, Signature=" + someTx.getRealSignature().toString() + '}',
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
        assertArrayEquals(secp256k1Signature.bytes(), ethereumTransaction.getRealSignature().bytes());

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

    private RawTransaction[] generateTransactions(int count) {
        var to = "0x0000000000000000000000000000000000001337";
        var data = "01020304050607080a";
        var gas = BigInteger.valueOf(21000);
        var gasPrice = BigInteger.valueOf(2000000000);
        var txs = new RawTransaction[count];
        for (int i = 0; i < txs.length; i++) {
            var nonce = BigInteger.valueOf(i);
            var value = BigInteger.valueOf(i).multiply(BigInteger.TEN.pow(18));
//            if (i % 3 == 0) {
            txs[i] = RawTransaction.createTransaction(nonce, gasPrice, gas, to, value, data);
//            } else {
//                txs[i] = RawTransaction.createTransaction(0, nonce, gas, to, value, data, gasPrice, gasPrice);
//            }
        }
        return txs;
    }

    @Test
    public void ethereumTransactionsRootHashTest() {
        // source of these hashes: TestHashRoot() at libevm/lib/service_hash_test.go:103
        final var testCases =
                Map.ofEntries(
                        entry(0, "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"),
                        entry(1, "0x3a7a98166e45e65843ba0ee347221fe4605eb87765675d1041a8b35c47d9daa9"),
                        entry(2, "0x7723d5115e8991cd655cbc25b52c57404f0ec85fdc2c5fd74258dec7f7c65f5f"),
                        entry(3, "0xc7ed4aa41206c45768399878e1b58fcdda6716b94678753378ffaa9a0a091a39"),
                        entry(4, "0xb46ac0b787038ed05e5176b1c957b63254c3292634495bc93b96a8e1d91d680f"),
                        entry(10, "0x4826a12ac6cc1a74be1ee0d1fbfdd18c4f1d0fe3ca295a4d1f15ee2e1ac5dc82"),
                        entry(51, "0xf333fabbe32aafcba45a47392478ad34420222442a25b6342985ea12efd3cd3b"),
                        entry(1000, "0x5bd20957d54171a32d9322a5c10de80613575daa7fc1eecaf08e37a7fa0de27a"),
                        entry(126, "0xbee10338305eed034a9e27028e1c8146f932149feef02d0653a896f074f6d678"),
                        entry(127, "0x362ed36adec0debb81fa47acdce2a2d00c1b0e54d6d294603609702fae5db61c"),
                        entry(128, "0xb1fbaff3745dfd7bb90f7a0cd1445b150cedf185de9d6fc8db5eb48de1b399cc"),
                        entry(129, "0x55387d947f5417b3a77bab638ae6cd55afa741a63c4a123c5ed747444b9eeb83"),
                        entry(130, "0x0b25f104de56321cf444f41bb18504678967fd5e680e9a56adc78b6cbbe18bf3"),
                        entry(765, "0x829b8081bf15bd0b201cf57d9033e51afb6117de92c051cb9ea7ddd1181642f5")
                );
        for (var testCase : testCases.entrySet()) {
            final var txs = generateTransactions(testCase.getKey());
            final var rlpTxs = Arrays.stream(txs).map(tx -> TransactionEncoder.encode(
                    tx,
                    // make sure we also encode a signature, even if it is empty to make the results comparable to GETH
                    new Sign.SignatureData(new byte[0], new byte[0], new byte[0])
            )).toArray(byte[][]::new);
            final var actualHash = Numeric.toHexString(TrieHasher.Root(rlpTxs));
            assertEquals("should match transaction root hash", testCase.getValue(), actualHash);
        }
    }
}
