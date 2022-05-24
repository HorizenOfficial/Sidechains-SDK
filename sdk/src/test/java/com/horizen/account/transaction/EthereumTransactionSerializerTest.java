package com.horizen.account.transaction;

import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.PublicKeySecp256k1Proposition;
import com.horizen.transaction.TransactionSerializer;
import org.bouncycastle.util.Strings;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import scala.util.Try;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EthereumTransactionSerializerTest {
    EthereumTransaction ethereumTransaction;

    @Before
    public void BeforeEachTest() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // Create the raw Transaction
        String payload = "This is string to sign";
        var message = payload.getBytes(StandardCharsets.UTF_8);
        var someValue = BigInteger.ONE;
        var rawTX = RawTransaction.createTransaction(someValue,
                someValue, someValue, "0x", someValue, "");

        // Create a key pair, create tx signature and create ethereum Transaction
        ECKeyPair pair = Keys.createEcKeyPair();
        var msgSignature = Sign.signMessage(message, pair, true);
        var txSignature = new SignatureSecp256k1(msgSignature);
        var txProposition = new PublicKeySecp256k1Proposition(Strings.toByteArray("0x" + Keys.getAddress(pair)));
        ethereumTransaction = new EthereumTransaction(rawTX, txSignature, txProposition);
    }

    @Test
    public void ethereumTransactionSerializeTest() {
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
}
