package io.horizen.account.transaction;

import io.horizen.account.fixtures.EthereumTransactionFixture;
import io.horizen.account.proof.SignatureSecp256k1;
import io.horizen.account.state.GasUtil;
import io.horizen.transaction.exception.TransactionSemanticValidityException;
import io.horizen.utils.BytesUtils;
import org.junit.Test;
import java.math.BigInteger;
import java.util.Optional;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EthereumTransactionSemanticValidityTest implements EthereumTransactionFixture {

    private void assertNotValid(EthereumTransaction tx) {
        try {
            tx.semanticValidity();
            fail("Failure expected" );
        } catch (TransactionSemanticValidityException e) {
            // mostly expected
            System.out.println(e);
        } catch (Throwable t) {
            System.out.println(t);
            fail("TransactionSemanticValidityException expected" );
        }
    }

    @Test
    public void testUnsignedEip155TxValidity() {
        var transaction = getUnsignedEip155LegacyTransaction();
        assertNotValid(transaction);
    }

    @Test
    public void testUnsignedEip1559TxValidity() {
        var transaction = getUnsignedEoa2EoaEip1559Transaction();
        assertNotValid(transaction);
    }

    @Test
    public void testEoa2EoaEip155LegacyValidity() {
        var goodTx = getEoa2EoaEip155LegacyTransaction();
        try {
            goodTx.semanticValidity();
        } catch (Throwable e) {
            fail("Test1: Successful EthereumTransaction creation expected." + e);
        }

        // 1. null chainId
        // 1.1 null obj is ok, this will be a legacy non eip155 tx
        try {
            var tx = copyEip155LegacyEthereumTransaction(goodTx, Optional.empty(),
                        null, null, null, null,
                        null, null,
                        null);
            tx.semanticValidity();
            assertTrue(!tx.isEIP155());
        } catch (Throwable e) {
            fail("Test1: Successful EthereumTransaction creation expected." + e);
        }

        // 1.2 negative chainId
        assertNotValid(
                copyEip155LegacyEthereumTransaction(goodTx, Optional.of(goodTx.getChainId()*(-1)),
                        null, null, null, null,
                        null, null,
                        null)
        );

        // 1.3 zero chainId

        assertNotValid(
                copyEip155LegacyEthereumTransaction(goodTx, Optional.of(0L),
                        null,null, null, null,
                        null, null,
                        null)
        );


    }

    @Test
    public void testEoa2EoaLegacyValidity() {
        var goodTx = getEoa2EoaLegacyTransaction();
        try {
            goodTx.semanticValidity();
        } catch (Throwable e) {
            fail("Test1: Successful EthereumTransaction creation expected." + e);
        }

        // 1. bad gasPrice
        // 1.1 negative
        assertNotValid(
                copyLegacyEthereumTransaction(goodTx,
                        null, null, null, goodTx.getGasPrice().negate(),
                        null, null,
                        null)
        );

        // 1.2 not an uint256
        assertNotValid(
                copyLegacyEthereumTransaction(goodTx,
                        null, null, null,
                        new BigInteger(BytesUtils.fromHexString("220000000000000000000000000000000000000000000000000000000011223344")),
                        null,
                        null,null)
        );

    }

    @Test
    public void testEoa2EoaEip1559Validity() {
        var goodTx = getEoa2EoaEip1559Transaction();
        try {
          goodTx.semanticValidity();
        } catch (Throwable e) {
            fail("Test1: Successful EthereumTransaction creation expected." + e);
        }

        var goodR = goodTx.getSignature().getR();
        var goodS = goodTx.getSignature().getS();

        // negative tests
        // 1. negative nonce
        assertNotValid(
            copyEip1599EthereumTransaction(goodTx,
                null, null, goodTx.getNonce().negate(),
                null, null, null, null, null, null)
        );


        // 2. Bad signature
        // 2.1 - invalid v-value
        // The V header byte should be in the range [27, 34]
        // (practically the only used values in ethereum signature scheme are 27 and 28)
        try {
            Optional.of(new SignatureSecp256k1(
                    new BigInteger("07",16),
                    goodR,
                    goodS
            ));

            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // 2.2 - Semantically valid V but not used in ethereum signature scheme (see above)
        try {
            var badSignOpt1B = Optional.of(new SignatureSecp256k1(
                new BigInteger("1f", 16),
                goodR,
                goodS
            ));

            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // 2.3 - null v-value array
        try {
            var badSignOpt2 = Optional.of(new SignatureSecp256k1(
                    null,
                    goodR,
                    goodS
            ));
            copyEip1599EthereumTransaction(goodTx,
                    null, null, null,
                    null, null, null, null, null,
                    badSignOpt2
            );
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // 2.4 - null r-value array
        try {
            var badSignOpt3 = Optional.of(new SignatureSecp256k1(
                    new BigInteger("1c", 16),
                    null,
                    goodS
            ));
            fail("IllegalArgumentException expected");

            copyEip1599EthereumTransaction(goodTx,
                    null, null, null,
                    null, null, null, null, null,
                    badSignOpt3
            );
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // 2.5 - Invalid r-value
        BigInteger upper_r_value = new BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16);
        try {
            Optional.of(new SignatureSecp256k1(
                    new BigInteger("1c", 16),
                    upper_r_value.add(BigInteger.ONE),
                    goodS
            ));
            fail("IllegalArgumentException expected");

        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // 2.6 - Invalid s-value
        BigInteger upper_s_value = new BigInteger("7fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f46681b20a0", 16);
        try {
            Optional.of(new SignatureSecp256k1(
                new BigInteger("1c", 16),
                goodR,
                upper_s_value.add(BigInteger.ONE)
            ));
            fail("IllegalArgumentException expected");

        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }


        // 3. Bad to address
        // 3.1 - invalid hex string
        try {
            copyEip1599EthereumTransaction(goodTx,
                    null, Optional.of("0x11223344556677889900112233445566778899xx"), null,
                    null, null, null, null, null,
                    null
            );
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // 3.2 - Short string as to address
        try {
            copyEip1599EthereumTransaction(goodTx,
                    null, Optional.of("0x11223344556677889900112233445566778899"), null,
                    null, null, null, null, null,
                    null
            );
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // 3.3 - Longer string as to address
        try {
            copyEip1599EthereumTransaction(goodTx,
                    null, Optional.of("0x1122334455667788990011223344556677889900aa"),null,
                    null, null, null, null, null,
                    null);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e){
            //  expected
            System.out.println(e);
        } catch (Throwable t) {
            System.out.println(t);
            fail("IllegalArgumentException expected");
        }

        // 4. negative tx value
        assertNotValid(
                copyEip1599EthereumTransaction(goodTx,
                        null, null, null,
                        null, null, null, goodTx.getValue().negate(),
                        null,null)
        );

        // 5. bad gas limit
        // 5.1 negative
        assertNotValid(
                copyEip1599EthereumTransaction(goodTx,
                        null, null, null,
                        goodTx.getGasLimit().negate(), null, null, null,
                        null,null)
        );

        // 5.2 null
        assertNotValid(
                copyEip1599EthereumTransaction(goodTx,
                        null, null, null,
                        BigInteger.ZERO, null, null, null,
                        null,null)
        );

        // 5.3 not an uint64 (throws GasUintOverflowException)
        assertNotValid(
                copyEip1599EthereumTransaction(goodTx,
                        null, null, null,
                        new BigInteger(BytesUtils.fromHexString("220000000011223344")), null, null, null,
                        null,null)
        );

        // 6. bad maxFeePerGas
        // 6.1 negative
        assertNotValid(
                copyEip1599EthereumTransaction(goodTx,
                        null, null, null,
                        null, null, goodTx.getMaxFeePerGas().negate(), null,
                        null,null)
        );

        // 6.2 not an uint256
        assertNotValid(
                copyEip1599EthereumTransaction(goodTx,
                        null, null, null,
                        null, null,
                        new BigInteger(BytesUtils.fromHexString("220000000000000000000000000000000000000000000000000000000011223344")),
                        null,null,null)
        );

        // 7. bad maxPriorityFeePerGas
        // 7.1 negative
        assertNotValid(
                copyEip1599EthereumTransaction(goodTx,
                        null, null, null,
                        null, goodTx.getMaxPriorityFeePerGas().negate(), null, null,
                        null,null)
        );
        // 7.2 not an uint256
        assertNotValid(
                copyEip1599EthereumTransaction(goodTx,
                        null, null, null,
                        null,
                        new BigInteger(BytesUtils.fromHexString("220000000000000000000000000000000000000000000000000000000011223344")),
                        null,null,null,null)
        );

        // 8. gasLimit below intrinsic gas for eoa2eoa
        assertNotValid(
                copyEip1599EthereumTransaction(goodTx,
                        null, null, null,
                        GasUtil.TxGas().subtract(BigInteger.ONE), null, null, null,
                        null,null)
        );

        //  maxPriorityFeePerGas [%s] higher than maxFeePerGas
        assertNotValid(
                copyEip1599EthereumTransaction(goodTx,
                        null, null, null,
                        null, goodTx.getMaxFeePerGas().add(BigInteger.ONE), null, null,
                        null,null)
        );

        // 9 bad chainId
        // 9.1 zero chainId
        assertNotValid(
                copyEip1599EthereumTransaction(goodTx,
                        0L, null, null,
                        null, null, null, null,
                        null,null)
        );

        // 9.2 negative chainId
        assertNotValid(
                copyEip1599EthereumTransaction(goodTx,
                        -1L, null, null,
                        null, null, null, null,
                        null,null)
        );
    }

    @Test
    public void testContractDeploymentValidity() {
        var goodTx = getContractDeploymentEip1559Transaction();
        try {
            goodTx.semanticValidity();
        } catch (Throwable e) {
            fail("Test1: Successful EthereumTransaction creation expected." + e);
        }

        // negative tests
        // 1. null data
        assertNotValid(
                copyEip1599EthereumTransaction(goodTx,
                        null, null, null,
                        null, null, null, null, "", null)
        );

        // 2. gasLimit below intrinsic gas, computed without
        assertNotValid(
                copyEip1599EthereumTransaction(goodTx,
                        null,null, null,
                        GasUtil.TxGasContractCreation().subtract(BigInteger.ONE), null, null, null,
                        null,null)
        );
    }

    @Test
    public void testBigTxValidity() throws TransactionSemanticValidityException {
        // data are charged with gas therefore we must set a gasLimt large enough.
        // As of now check on block max gas limit is not made in tx.semanticValidity (it is made in state.validate(tx))
        var goodTx = getBigDataTransaction(5000000, BigInteger.valueOf(100000000));
        goodTx.semanticValidity();
    }
}
