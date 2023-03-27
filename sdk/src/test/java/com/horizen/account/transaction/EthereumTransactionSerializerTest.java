package com.horizen.account.transaction;

import com.horizen.account.fixtures.EthereumTransactionFixture;
import com.horizen.account.secret.PrivateKeySecp256k1;
import com.horizen.account.secret.PrivateKeySecp256k1Serializer;
import com.horizen.account.state.GasUtil;
import com.horizen.account.utils.Secp256k1;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.utils.BytesUtils;
import org.junit.Test;
import scala.Option;
import scala.util.Try;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.math.BigInteger;

import static org.junit.Assert.*;

public class EthereumTransactionSerializerTest implements EthereumTransactionFixture {

    // Check that using the same key pair for signing two transactions give the same from address
    @Test
    public void checkSigningTxTest() {

        String privateKeyHex = "227dbb8586117d55284e26620bc76534dfbd2394be34cf4a09cb775d593b6f2b";
        PrivateKeySecp256k1 privKey = PrivateKeySecp256k1Serializer.getSerializer().parseBytes(BytesUtils.fromHexString(privateKeyHex));
        var account1Key = Option.apply(privKey);

        var nonce = BigInteger.valueOf(0);
        var value = BigInteger.valueOf(11);
        var gasPrice = BigInteger.valueOf(12);
        var gasLimit = GasUtil.TxGas();

        var tx1 = createLegacyTransaction(value, nonce, account1Key, gasPrice, gasLimit);

        try {
            tx1.semanticValidity();
        } catch (Throwable t) {
            fail("Expected a valid tx: " + t.getMessage());
        }

        var tx2 = createLegacyTransaction(value, nonce.add(BigInteger.ONE), account1Key, gasPrice, gasLimit);

        try {
            tx2.semanticValidity();
        } catch (Throwable t) {
            fail("Expected a valid tx: " + t.getMessage());
        }

        // different signatures but same from address
        assertNotEquals(tx1.getSignature(), tx2.getSignature());
        assertEquals(tx1.getFrom(), tx2.getFrom());

        var maxFeePerGas = BigInteger.valueOf(15);
        var maxPriorityFeePerGas = BigInteger.valueOf(15);
        var tx3 = createEIP1559Transaction(value, nonce.add(BigInteger.ONE), account1Key, maxFeePerGas, maxPriorityFeePerGas, gasLimit);

        try {
            tx3.semanticValidity();
        } catch (Throwable t) {
            fail("Expected a valid tx: " + t.getMessage());
        }

        // different signatures but same from address
        assertNotEquals(tx1.getSignature(), tx3.getSignature());
        assertEquals(tx1.getFrom(), tx3.getFrom());

        var tx4 = createLegacyEip155Transaction(value, nonce.add(BigInteger.ONE), account1Key, gasPrice, gasLimit);

        try {
            tx4.semanticValidity();
        } catch (Throwable t) {
            fail("Expected a valid tx: " + t.getMessage());
        }

        // different signatures but same from address
        assertNotEquals(tx1.getSignature(), tx4.getSignature());
        assertEquals(tx1.getFrom(), tx4.getFrom());
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
    public void regressionTestPartiallySignedEip155() {
        EthereumTransaction transaction = getPartiallySignedEip155LegacyTransaction();
        doTest(transaction, "ethereumtransaction_eoa2eoa_eip155_legacy_partially_signed_hex", false);
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
        System.out.println(transaction.id());
        System.out.println(parsedTransaction.id());
        assertEquals("Transaction is different to the origin.", transaction.id(), parsedTransaction.id());
    }

    // Yuma M3-V4 regression tests: must be compatible with both m3-v2 and m3-v23 tx signatures approaches:
    @Test
    public void legacyTxWithLeadingZerosR() {
        // Tx was generated using EVM-M3-V2
        String txHex = "f861808227108252089412345678901234567890123456789012345678900b801ba000448d07650c668932e3fcd69d0d5ac1e679c214da376d61af0c4af9c92590b8a0514abbd34a7d9d358e757a15e48af00e705d24d53e34bca81e04c4afb81514f3";

        EthereumTransaction tx = EthereumTransactionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(txHex));

        assertEquals("32 bytes expected", Secp256k1.SIGNATURE_RS_SIZE, tx.getSignature().getR().length);
        assertEquals("leading zeros expected", 0x0, tx.getSignature().getR()[0]);

        assertEquals("Tx encoded wrongly", txHex, BytesUtils.toHexString(tx.bytes()));
    }

    @Test
    public void legacyTxWithLeadingZerosS() {
        // Tx was generated using EVM-M3-V2
        String txHex = "f861808227108252089412345678901234567890123456789012345678900b801ca02c148403c2977a2aacae7deacfd92e416025388aa35b1143dd382bdc404bf1b2a0002920d154a5bc957f138f668222da96a724477ba6cf0becda04c8e5cbae9486";
        EthereumTransaction tx = EthereumTransactionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(txHex));

        assertEquals("32 bytes expected", Secp256k1.SIGNATURE_RS_SIZE, tx.getSignature().getS().length);
        assertEquals("leading zeros expected", 0x0, tx.getSignature().getS()[0]);

        assertEquals("Tx encoded wrongly", txHex, BytesUtils.toHexString(tx.bytes()));
    }

    @Test
    public void eip1559TxWithLeadingZerosR() {
        // Tx was generated using EVM-M3-V2
        String txHex = "02f8688207cd808227108227108252089412345678901234567890123456789012345678900b80c001a0009d6cc3c0079cc7970389db7eaa4e4d21514fb14546c16469507204dd03bb3ba03ba5cc2f88fd493ac3e456561d6775aa220f980fb5ca061445ce3af7a4ab021f";
        EthereumTransaction tx = EthereumTransactionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(txHex));

        assertEquals("32 bytes expected", Secp256k1.SIGNATURE_RS_SIZE, tx.getSignature().getR().length);
        assertEquals("leading zeros expected", 0x0, tx.getSignature().getR()[0]);

        assertEquals("Tx encoded wrongly", txHex, BytesUtils.toHexString(tx.bytes()));
    }

    @Test
    public void eip1559TxWithLeadingZerosS() {
        // Tx was generated using EVM-M3-V2
        String txHex = "02f8688207cd808227108227108252089412345678901234567890123456789012345678900b80c001a0ad9709d2e63d73255fe636b72819d5abe419842edbd2c45555bd408dbf7f7e07a000aeb4b920b25aee8eefc039b65b178792ec31bed634a8169390ac8d3a1aeceb";
        EthereumTransaction tx = EthereumTransactionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(txHex));

        assertEquals("32 bytes expected", Secp256k1.SIGNATURE_RS_SIZE, tx.getSignature().getS().length);
        assertEquals("leading zeros expected", 0x0, tx.getSignature().getS()[0]);

        assertEquals("Tx encoded wrongly", txHex, BytesUtils.toHexString(tx.bytes()));
    }

    @Test
    public void legacyTxWithShorterR() {
        // Tx was generated using EVM-M3-V3
        String txHex = "f860808227108252089412345678901234567890123456789012345678900b801c9f4e00433c65d41a94e5d222d02065fa6d86769f843a5d8ee9d0c40d3065a48fa0148e6a3ddf97df4796b339d27844d61a8f17d8dc5e76c5fb2cedbb08e31778dc";
        EthereumTransaction tx = EthereumTransactionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(txHex));

        assertEquals("<32 bytes expected", Secp256k1.SIGNATURE_RS_SIZE-1, tx.getSignature().getR().length);

        assertEquals("Tx encoded wrongly", txHex, BytesUtils.toHexString(tx.bytes()));
    }

    @Test
    public void legacyTxWithShorterS() {
        // Tx was generated using EVM-M3-V3
        String txHex = "f860808227108252089412345678901234567890123456789012345678900b801ba075e07b8dc5fe5ab96605cd3ebf973c6e5bbbd39a54072597bddcaa581bbb20249f417c71e244d5589ac3504b4c7fa576fbd933b9674818278c686d4609962eb6";
        EthereumTransaction tx = EthereumTransactionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(txHex));

        assertEquals("<32 bytes expected", Secp256k1.SIGNATURE_RS_SIZE-1, tx.getSignature().getS().length);

        assertEquals("Tx encoded wrongly", txHex, BytesUtils.toHexString(tx.bytes()));
    }

    @Test
    public void eip1559TxWithShorterR() {
        // Tx was generated using EVM-M3-V3
        String txHex = "02f8678207cd808227108227108252089412345678901234567890123456789012345678900b80c0809f4f1b6c5fd3d16f3648c91f269db9ab8eaa96280870c51774b5b4d5827a5435a033c4aa9eb87257fe938013837900f1039a14423be081d7ea02d93be7b6a9a1e2";
        EthereumTransaction tx = EthereumTransactionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(txHex));

        assertEquals("<32 bytes expected", Secp256k1.SIGNATURE_RS_SIZE-1, tx.getSignature().getR().length);

        assertEquals("Tx encoded wrongly", txHex, BytesUtils.toHexString(tx.bytes()));
    }

    @Test
    public void eip1559TxWithShorterS() {
        // Tx was generated using EVM-M3-V3
        String txHex = "02f8678207cd808227108227108252089412345678901234567890123456789012345678900b80c080a0261d1633abe063555cc8354278a5d0c3f1697a4e468d73968be679c1355a69899f660d75b1dbdda9613ff958fee1f3e6be8a9f1be7b64747ee2738200b3f76dc";
        EthereumTransaction tx = EthereumTransactionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(txHex));

        assertEquals("<32 bytes expected", Secp256k1.SIGNATURE_RS_SIZE-1, tx.getSignature().getS().length);

        assertEquals("Tx encoded wrongly", txHex, BytesUtils.toHexString(tx.bytes()));
    }
}
