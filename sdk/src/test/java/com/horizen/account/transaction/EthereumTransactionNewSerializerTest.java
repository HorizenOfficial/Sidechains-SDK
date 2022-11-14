package com.horizen.account.transaction;

import com.horizen.account.fixtures.EthereumTransactionNewFixture;
import com.horizen.account.state.GasUtil;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.utils.BytesUtils;
import org.junit.Test;
import org.web3j.crypto.ECKeyPair;
import scala.Option;
import scala.util.Try;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Optional;

import static org.junit.Assert.*;

public class EthereumTransactionNewSerializerTest implements EthereumTransactionNewFixture {

    /*
    EthereumTransactionNew ethereumTransaction;
    EthereumTransactionNew signedEthereumTransaction;

    private void initSerializationTest() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // Create the raw Transaction
        String payload = "This is string to sign";
        var message = payload.getBytes(StandardCharsets.UTF_8);
        var someValue = BigInteger.ONE;
        var rawTransaction = RawTransaction.createTransaction(someValue,
                someValue, someValue, "0x", someValue, "");

        // Create a key pair, create tx signature and create ethereum Transaction
        ECKeyPair pair = Keys.createEcKeyPair();
        var msgSignature = Sign.signMessage(message, pair, true);

        var signedRawTransaction = new SignedRawTransaction(someValue,
                someValue, someValue, "0x", someValue, "",
                msgSignature);
        ethereumTransaction = new EthereumTransactionNew(rawTransaction);
        signedEthereumTransaction = new EthereumTransactionNew(signedRawTransaction);
    }

    @Test
    public void ethereumTransactionSerializeTest() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        initSerializationTest();

        // Get transaction serializer and serialize
        TransactionSerializer serializer = ethereumTransaction.serializer();
        byte[] bytes = serializer.toBytes(ethereumTransaction);

        // Test 1: Correct bytes deserialization
        Try<EthereumTransactionNew> t = serializer.parseBytesTry(bytes);

        assertTrue("Transaction serialization failed.", t.isSuccess());

        assertEquals("Deserialized transactions expected to be equal", ethereumTransaction.toString(), t.get().toString());

        // Test 2: try to parse broken bytes
        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);
    }

    @Test
    public void ethereumTransactionSerializeSignedTest() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        initSerializationTest();

        // Get transaction serializer and serialize
        TransactionSerializer serializer = signedEthereumTransaction.serializer();
        byte[] bytes = serializer.toBytes(signedEthereumTransaction);

        // Test 1: Correct bytes deserialization
        Try<EthereumTransactionNew> t = serializer.parseBytesTry(bytes);

        assertTrue("Transaction serialization failed.", t.isSuccess());

        assertEquals("Deserialized transactions expected to be equal", signedEthereumTransaction.toString(), t.get().toString());

        // Test 2: try to parse broken bytes
        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);
    }
    */

    // Check that using the same key pair for signing two transactions give the same from address
    @Test
    public void checkSigningTxTest() {

        var privKey = new BigInteger("49128115046059273042656529250771669375541382406576940612305697909438063650480");
        var pubKey = new BigInteger("8198110339830204259458045104072783158824826560452916203793316544691619725076836725487148486981196842344156645049311437356787993166435716250962254331877695");
        var account1KeyPair = Option.apply(new ECKeyPair(privKey, pubKey));

        var nonce = BigInteger.valueOf(0);
        var value = BigInteger.valueOf(11);
        var gasPrice = BigInteger.valueOf(12);
        var gasLimit = GasUtil.TxGas();

        var tx1 = createLegacyTransaction(value, nonce, account1KeyPair, gasPrice, gasLimit);

        try {
            tx1.semanticValidity();
        } catch (Throwable t) {
            fail("Expected a valid tx: " + t.getMessage());
        }

        var tx2 = createLegacyTransaction(value, nonce.add(BigInteger.ONE), account1KeyPair, gasPrice, gasLimit);

        try {
            tx2.semanticValidity();
        } catch (Throwable t) {
            fail("Expected a valid tx: " + t.getMessage());
        }

        // different signatures but same from address
        assertTrue(!tx1.getSignature().equals(tx2.getSignature()));
        assertEquals(tx1.getFromAddress(), tx2.getFromAddress());

        var maxFeePerGas = BigInteger.valueOf(15);
        var maxPriorityFeePerGas = BigInteger.valueOf(15);
        var tx3 = createEIP1559Transaction(value, nonce.add(BigInteger.ONE), account1KeyPair, maxFeePerGas, maxPriorityFeePerGas, gasLimit);

        try {
            tx3.semanticValidity();
        } catch (Throwable t) {
            fail("Expected a valid tx: " + t.getMessage());
        }

        // different signatures but same from address
        assertTrue(!tx1.getSignature().equals(tx3.getSignature()));
        assertEquals(tx1.getFromAddress(), tx3.getFromAddress());

        var tx4 = createLegacyEip155Transaction(value, nonce.add(BigInteger.ONE), account1KeyPair, gasPrice, gasLimit);

        try {
            tx4.semanticValidity();
        } catch (Throwable t) {
            fail("Expected a valid tx: " + t.getMessage());
        }

        // different signatures but same from address
        assertTrue(!tx1.getSignature().equals(tx4.getSignature()));
        assertEquals(tx1.getFromAddress(), tx4.getFromAddress());
    }

    @Test
    public void regressionTestLegacySigned() {
        EthereumTransactionNew transaction = getEoa2EoaLegacyTransaction();
        doTest(transaction, "ethereumtransaction_eoa2eoa_legacy_signed_hex", false);
    }

    @Test
    public void regressionTestLegacyUnsigned() {
        EthereumTransactionNew transaction = getUnsignedEoa2EoaLegacyTransaction();
        doTest(transaction, "ethereumtransaction_eoa2eoa_legacy_unsigned_hex", false);
    }

    @Test
    public void regressionTestEoa2EoaEip1559() {
        EthereumTransactionNew transaction = getEoa2EoaEip1559Transaction();
        doTest(transaction, "ethereumtransaction_eoa2eoa_eip1559_signed_hex", false);
    }

    @Test
    public void regressionTestEoa2EoaEip1559Unsigned() {
        EthereumTransactionNew transaction = getUnsignedEoa2EoaEip1559Transaction();
        doTest(transaction, "ethereumtransaction_eoa2eoa_eip1559_unsigned_hex", false);
    }

    @Test
    public void regressionTestEip155() {
        EthereumTransactionNew transaction = getEoa2EoaEip155LegacyTransaction();
        doTest(transaction, "ethereumtransaction_eoa2eoa_eip155_legacy_signed_hex", false);
    }

    @Test
    public void regressionTestUnsignedEip155() {
        EthereumTransactionNew transaction = getUnsignedEip155LegacyTransaction();
        doTest(transaction, "ethereumtransaction_eoa2eoa_eip155_legacy_unsigned_hex", false);
    }

    @Test
    public void regressionTestContractDeploymentEip1559() {
        EthereumTransactionNew transaction = getContractDeploymentEip1559Transaction();
        doTest(transaction, "ethereumtransaction_contract_deployment_eip1559_hex", false);
    }

    @Test
    public void regressionTestContractCallEip155Legacy() {
        EthereumTransactionNew transaction = getContractCallEip155LegacyTransaction();
        doTest(transaction, "ethereumtransaction_contract_call_eip155_legacy_hex", false);
    }

    private void doTest(EthereumTransactionNew transaction, String hexFileName, boolean writeMode) {
        // Set `true` and run if you want to update regression data.
        if (writeMode) {
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter("src/test/resources/" +
                        hexFileName));
                out.write(BytesUtils.toHexString(transaction.bytes()));
                out.close();
            } catch (Throwable e) {
                fail(e.toString());
                return;
            }
        }

        byte[] bytes;
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            FileReader file = new FileReader(classLoader.getResource(hexFileName).getFile());
            bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
        }
        catch (Exception e) {
            fail(e.toString());
            return;
        }

        TransactionSerializer serializer = transaction.serializer();
        Try<EthereumTransactionNew> t = serializer.parseBytesTry(bytes);
        assertTrue("Transaction serialization failed.", t.isSuccess());

        EthereumTransactionNew parsedTransaction = t.get();
        assertEquals("Transaction is different to the origin.", transaction.id(), parsedTransaction.id());
    }
}
