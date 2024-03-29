package io.horizen.account.transaction;

import io.horizen.account.fixtures.EthereumTransactionFixture;
import io.horizen.account.fork.Version1_3_0Fork;
import io.horizen.account.secret.PrivateKeySecp256k1;
import io.horizen.account.secret.PrivateKeySecp256k1Serializer;
import io.horizen.account.state.GasUtil;
import io.horizen.fork.ForkManagerUtil;
import io.horizen.fork.OptionalSidechainFork;
import io.horizen.fork.SidechainForkConsensusEpoch;
import io.horizen.fork.SimpleForkConfigurator;
import io.horizen.transaction.TransactionSerializer;
import io.horizen.utils.BytesUtils;
import io.horizen.utils.Pair;
import org.junit.Before;
import org.junit.Test;
import scala.Option;
import scala.util.Try;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class EthereumTransactionSerializerTest implements EthereumTransactionFixture {

    final static int VERSION_1_3_FORK_EPOCH = 35;
    final static int DEFAULT_CONSENSUS_EPOCH = 0;

    @Before
    public void setUp() {
        SimpleForkConfigurator forkConfigurator = new SimpleForkConfigurator() {
            public List<Pair<SidechainForkConsensusEpoch, OptionalSidechainFork>> getOptionalSidechainForks() {
                var listOfForks = new ArrayList<Pair<SidechainForkConsensusEpoch, OptionalSidechainFork>>();
                listOfForks.add(
                        new Pair<>(
                                new SidechainForkConsensusEpoch(VERSION_1_3_FORK_EPOCH, VERSION_1_3_FORK_EPOCH, VERSION_1_3_FORK_EPOCH),
                                new Version1_3_0Fork(true)
                        )
                );
                return listOfForks;
            }
        };

        ForkManagerUtil.initializeForkManager(forkConfigurator,"regtest");
    }

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
            tx1.semanticValidity(DEFAULT_CONSENSUS_EPOCH);
            tx1.semanticValidity(VERSION_1_3_FORK_EPOCH);
        } catch (Throwable t) {
            fail("Expected a valid tx: " + t.getMessage());
        }

        var tx2 = createLegacyTransaction(value, nonce.add(BigInteger.ONE), account1Key, gasPrice, gasLimit);

        try {
            tx2.semanticValidity(DEFAULT_CONSENSUS_EPOCH);
            tx2.semanticValidity(VERSION_1_3_FORK_EPOCH);
        } catch (Throwable t) {
            fail("Expected a valid tx: " + t.getMessage());
        }

        // different signatures but same from address
        assertNotEquals(tx1.getSignature(), tx2.getSignature());
        assertEquals(tx1.getFrom(), tx2.getFrom());

        var maxFeePerGas = BigInteger.valueOf(15);
        var maxPriorityFeePerGas = BigInteger.valueOf(15);
        var tx3 = createEIP1559Transaction(value, nonce.add(BigInteger.ONE), account1Key, maxFeePerGas, maxPriorityFeePerGas, gasLimit, new byte[0]);

        try {
            tx3.semanticValidity(DEFAULT_CONSENSUS_EPOCH);
            tx3.semanticValidity(VERSION_1_3_FORK_EPOCH);
        } catch (Throwable t) {
            fail("Expected a valid tx: " + t.getMessage());
        }

        // different signatures but same from address
        assertNotEquals(tx1.getSignature(), tx3.getSignature());
        assertEquals(tx1.getFrom(), tx3.getFrom());

        var tx4 = createLegacyEip155Transaction(value, nonce.add(BigInteger.ONE), account1Key, gasPrice, gasLimit);

        try {
            tx4.semanticValidity(DEFAULT_CONSENSUS_EPOCH);
            tx4.semanticValidity(VERSION_1_3_FORK_EPOCH);
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
}
