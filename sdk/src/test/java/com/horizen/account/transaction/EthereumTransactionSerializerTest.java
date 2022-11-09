package com.horizen.account.transaction;

import com.horizen.account.fixtures.EthereumTransactionFixture;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.utils.BytesUtils;
import org.junit.Test;
import org.web3j.crypto.*;
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

    @Test
    public void regressionTestUnsignedEip155() {
        EthereumTransaction transaction = getUnsignedEip155LegacyTransaction();
        doTest(transaction, "ethereumtransaction_eoa2eoa_eip155_legacy_unsigned_hex", false);
    }

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
