package com.horizen.account.transaction;

import com.horizen.account.fixtures.EthereumTransactionFixture;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.utils.BytesUtils;
import org.junit.Test;
import org.web3j.crypto.*;
import scala.Option;
import scala.util.Try;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import static org.junit.Assert.*;
import static org.junit.Assert.fail;

public class EthereumTransactionSerializerTest implements EthereumTransactionFixture {

    EthereumTransaction ethereumTransaction;
    EthereumTransaction signedEthereumTransaction;

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
        ethereumTransaction = new EthereumTransaction(rawTransaction);
        signedEthereumTransaction = new EthereumTransaction(signedRawTransaction);
    }

    @Test
    public void ethereumTransactionSerializeTest() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        initSerializationTest();

        // Get transaction serializer and serialize
        TransactionSerializer serializer = ethereumTransaction.serializer();
        byte[] bytes = serializer.toBytes(ethereumTransaction);

        // Test 1: Correct bytes deserialization
        Try<EthereumTransaction> t = serializer.parseBytesTry(bytes);

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
        Try<EthereumTransaction> t = serializer.parseBytesTry(bytes);

        assertTrue("Transaction serialization failed.", t.isSuccess());

        assertEquals("Deserialized transactions expected to be equal", signedEthereumTransaction.toString(), t.get().toString());

        // Test 2: try to parse broken bytes
        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);
    }

    @Test
    public void checkSigningTxTest() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        //var account1KeyPair = Option.apply(Keys.createEcKeyPair());

        var privKey = new BigInteger("49128115046059273042656529250771669375541382406576940612305697909438063650480");
        var pubKey = new BigInteger("8198110339830204259458045104072783158824826560452916203793316544691619725076836725487148486981196842344156645049311437356787993166435716250962254331877695");
        var account1KeyPair = Option.apply(new ECKeyPair(privKey, pubKey));

        var nonce = BigInteger.valueOf(0);
        var value = BigInteger.valueOf(11);
        var gasPrice = BigInteger.valueOf(12);
        var gasLimit = BigInteger.valueOf(13);

        var tx1 = createLegacyTransaction(value, nonce, account1KeyPair, gasPrice, gasLimit);
        System.out.println(tx1.getSignature());
        System.out.println(tx1.getFromAddress());

        var tx2 = createLegacyTransaction(value, nonce.add(BigInteger.ONE), account1KeyPair, gasPrice, gasLimit);
        System.out.println(tx2.getSignature());
        System.out.println(tx2.getFromAddress());
    }

    @Test
    public void regressionTestLegacySigned() {
        EthereumTransaction transaction = getEoa2EoaLegacyTransaction();
        doTest(transaction, "ethereumtransaction_eoa2eoa_legacy_signed_hex", false);
    }

    @Test
    public void regressionTestLegacyUnsigned() {
        EthereumTransaction transaction = getUnsignedEoa2EoaLegacyTransaction();
        doTest(transaction, "ethereumtransaction_eoa2eoa_legacy_unsigned_hex", false);
    }

    @Test
    public void regressionTestEoa2EoaEip1559() {
        EthereumTransaction transaction = getEoa2EoaEip1559Transaction();
        doTest(transaction, "ethereumtransaction_eoa2eoa_eip1559_signed_hex", false);
    }

    @Test
    public void regressionTestEoa2EoaEip1559Unsigned() {
        EthereumTransaction transaction = getUnsignedEoa2EoaEip1559Transaction();
        doTest(transaction, "ethereumtransaction_eoa2eoa_eip1559_unsigned_hex", false);
    }

    @Test
    public void regressionTestEip155() {
        EthereumTransaction transaction = getEoa2EoaEip155LegacyTransaction();
        doTest(transaction, "ethereumtransaction_eoa2eoa_eip155_legacy_signed_hex", false);
    }

    /*
    @Test

    public void regressionTestUnsignedEip155() {
        EthereumTransaction transaction = getUnsignedEip155LegacyTransaction();
        doTest(transaction, "ethereumtransaction_eoa2eoa_eip155_legacy_unsigned_hex", false);
    }
    */

    @Test
    public void regressionTestContractDeploymentEip1559() {
        EthereumTransaction transaction = getContractDeploymentEip1559Transaction();
        doTest(transaction, "ethereumtransaction_contract_deployment_eip1559_hex", false);
    }

    @Test
    public void regressionTestContractCallEip155Legacy() {
        EthereumTransaction transaction = getContractCallEip155LegacyTransaction();
        doTest(transaction, "ethereumtransaction_contract_call_eip155_legacy_hex", false);
    }

    private void doTest(EthereumTransaction transaction, String hexFileName, boolean writeMode) {
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
        Try<EthereumTransaction> t = serializer.parseBytesTry(bytes);
        assertTrue("Transaction serialization failed.", t.isSuccess());

        EthereumTransaction parsedTransaction = t.get();
        assertEquals("Transaction is different to the origin.", transaction.id(), parsedTransaction.id());
    }
}
