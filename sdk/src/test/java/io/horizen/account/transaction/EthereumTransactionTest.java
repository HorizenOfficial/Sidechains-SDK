package io.horizen.account.transaction;

import io.horizen.account.proof.SignatureSecp256k1;
import io.horizen.account.proposition.AddressProposition;
import io.horizen.account.utils.EthereumTransactionDecoder;
import io.horizen.account.utils.EthereumTransactionUtils;
import io.horizen.evm.Address;
import io.horizen.evm.TrieHasher;
import io.horizen.utils.BytesUtils;
import org.junit.Test;
import org.web3j.utils.Numeric;
import sparkz.crypto.hash.Keccak256;
import sparkz.util.ByteArrayBuilder;
import sparkz.util.serialization.VLQByteBufferWriter;

import java.math.BigInteger;
import java.util.*;

import static java.util.Map.entry;
import static org.junit.Assert.*;

public class EthereumTransactionTest {


    public void checkEthTx(EthereumTransaction tx) {
        assertTrue(tx.getSignature().isValid(tx.getFrom(), tx.messageToSign()));
        String strBefore = tx.toString();
        byte[] encodedTx = EthereumTransactionSerializer.getSerializer().toBytes(tx);
        // read what *it wrote
        EthereumTransaction decodedTx = EthereumTransactionSerializer.getSerializer().parseBytes(encodedTx);
        String strAfter = decodedTx.toString();
        // we must have the same representation
        assertEquals(strBefore.toLowerCase(Locale.ROOT), strAfter.toLowerCase(Locale.ROOT));
        assertEquals(tx, decodedTx);
        assertTrue(decodedTx.getSignature().isValid(decodedTx.getFrom(), decodedTx.messageToSign()));
    }

    @Test
    public void transactionIdTests() {
        // expected ids were calculated using rlp encoder

        // EIP-1559
        var eipSignedTx = new EthereumTransaction(
                31337L,
                EthereumTransactionUtils.getToAddressFromString("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"),
                BigInteger.valueOf(0L),
                BigInteger.valueOf(1),
                BigInteger.valueOf(1),
                BigInteger.valueOf(1),
                BigInteger.valueOf(1),
                new byte[]{},
                new SignatureSecp256k1(
                        new BigInteger("1b", 16),
                        new BigInteger("805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023", 16),
                        new BigInteger("568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d", 16))
        );
        assertEquals("0x0dbd564dfb0c029e471d39a325d0b00bf9686f61f97f200e0b7299117eed51a8", "0x" + eipSignedTx.id());
        checkEthTx(eipSignedTx);

        // Legacy
        var legacyTx = new EthereumTransaction(
                EthereumTransactionUtils.getToAddressFromString("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"),
                BigInteger.valueOf(0L),
                BigInteger.valueOf(1),
                BigInteger.valueOf(1),
                BigInteger.valueOf(1),
                new byte[]{},
                new SignatureSecp256k1(
                        new BigInteger("1c", 16),
                        new BigInteger("2a4afbdd7e8d99c3df9dfd9e4ecd0afe018d8dec0b8b5fe1a44d5f30e7d0a5c5", 16),
                        new BigInteger("7ca554a8317ff86eb6b23d06fa210d23e551bed58f58f803a87e5950aa47a9e9", 16))
        );
        assertEquals("0x306f23ca4948f7b791768878eb540915d0e12bae54c0f7f2a119095de074dab6", "0x" + legacyTx.id());
        checkEthTx(legacyTx);

        // EIP-155 tx
        var unsignedEip155Tx = new EthereumTransaction(
                1L,
                EthereumTransactionUtils.getToAddressFromString("0x3535353535353535353535353535353535353535"),
                BigInteger.valueOf(9L),
                BigInteger.valueOf(20).multiply(BigInteger.TEN.pow(9)),
                BigInteger.valueOf(21000),
                BigInteger.TEN.pow(18),
                new byte[]{},
                null
        );
        var eip155Tx = new EthereumTransaction(
                1L,
                EthereumTransactionUtils.getToAddressFromString("0x3535353535353535353535353535353535353535"),
                BigInteger.valueOf(9L),
                BigInteger.valueOf(20).multiply(BigInteger.TEN.pow(9)),
                BigInteger.valueOf(21000),
                BigInteger.TEN.pow(18),
                new byte[]{},
                new SignatureSecp256k1(
                        new BigInteger("1b", 16),
                        new BigInteger("28EF61340BD939BC2195FE537567866003E1A15D3C71FF63E1590620AA636276", 16),
                        new BigInteger("67CBE9D8997F761AECB703304B3800CCF555C9F3DC64214B297FB1966A3B6D83", 16))
        );
        assertArrayEquals(unsignedEip155Tx.messageToSign(), eip155Tx.messageToSign());
        byte[] hash = (byte[]) Keccak256.hash(BytesUtils.fromHexString("f86c098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a76400008025a028ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276a067cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d83"));
        assertEquals(BytesUtils.toHexString(hash), eip155Tx.id());
        checkEthTx(eip155Tx);

    }

    @Test
    public void ethereumLegacyEIP155TransactionTest() {
        // Test 1: direct constructor test
        Long chainId = 1L;
        var someTx = new EthereumTransaction(
                chainId,
                EthereumTransactionUtils.getToAddressFromString("0x3535353535353535353535353535353535353535"),
                BigInteger.valueOf(9),
                BigInteger.valueOf(20).multiply(BigInteger.TEN.pow(9)),
                BigInteger.valueOf(21000),
                BigInteger.TEN.pow(18),
                new byte[]{},
                null
        );
        assertEquals("Chainid was not correct", chainId, someTx.getChainId());
        assertEquals("EIP-155 message to sign is incorrect", "0xec098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a764000080018080", "0x" + BytesUtils.toHexString(someTx.messageToSign()));


        // metamask eip155 tx:
        // - from address: 0x892278d9f50a1da5b2e98e5056f165b1b2486d97
        // - to address: 0xd830603264bd3118cf95f1fc623749337342f9e9
        // - value: 3000000000000000000
        // - chain id: 1997
        String metamaskHexStr = "f86d02843b9aca0082520894d830603264bd3118cf95f1fc623749337342f9e98829a2241af62c000080820fbea0f638802002d7c0a3115716f7d24d646452d598050ffc2d6892ba0ed88aeb76bea01dd5a7fb9ea0ec2a414dbb93b09b3e716a3cd05c1e333d40622c63d0c34e3d35";

        EthereumTransaction decodedTx = EthereumTransactionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(metamaskHexStr));
        long chainId2 = decodedTx.getChainId();
        var fromAddress = decodedTx.getFrom().address();
        var toAddress = decodedTx.getTo().map(AddressProposition::address).orElse(Address.ZERO);

        assertTrue(decodedTx.getSignature().isValid(decodedTx.getFrom(), decodedTx.messageToSign()));
        assertEquals("0x892278d9f50a1da5b2e98e5056f165b1b2486d97", fromAddress.toString());
        assertEquals("0xd830603264bd3118cf95f1fc623749337342f9e9", toAddress.toString());
        assertEquals(1997, chainId2);
        assertEquals("3000000000000000000", decodedTx.getValue().toString());

        // re-encode and check it is the same
        byte[] encodedTx = EthereumTransactionSerializer.getSerializer().toBytes(decodedTx);
        assertEquals(metamaskHexStr, BytesUtils.toHexString(encodedTx));
    }

    private EthereumTransaction[] generateTransactions(int count) {
        var to = EthereumTransactionUtils.getToAddressFromString("0x0000000000000000000000000000000000001337");
        var data = EthereumTransactionUtils.getDataFromString("01020304050607080a");
        var gas = BigInteger.valueOf(21000);
        var gasPrice = BigInteger.valueOf(2000000000);
        var txs = new EthereumTransaction[count];
        for (int i = 0; i < txs.length; i++) {
            var nonce = BigInteger.valueOf(i);
            var value = BigInteger.valueOf(i).multiply(BigInteger.TEN.pow(18));
//            if (i % 3 == 0) {

            // make sure we also have a signature to encode, even if it is empty to make the results comparable to GETH
            txs[i] = new EthereumTransaction(to, nonce, gasPrice, gas, value, data, null);
//            } else {
//                txs[i] = RawTransaction.createTransaction(0, nonce, gas, to, value, data, gasPrice, gasPrice);
//            }
        }
        return txs;
    }

    @Test
    public void ethereumTransactionsRootHashTest() {
        // source of these hashes: TestHashRoot() at libevm/lib/hash_test.go:102
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

            List<byte[]> rlpTxsList = new ArrayList<>();
            Arrays.asList(txs).forEach(
                ethTx -> {
                    VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
                    ethTx.encode(true, writer);
                    rlpTxsList.add(writer.toBytes());
                }
            );
            var rlpTxs2 = rlpTxsList.toArray(byte[][]::new);

            final var rlpTxs = Arrays.stream(txs).map(tx -> tx.encode(true)).toArray(byte[][]::new);
            int i = 0;
            while (i < rlpTxs.length) {
                assertArrayEquals(rlpTxs, rlpTxs2);
                i++;
            }

            final var actualHash = TrieHasher.Root(rlpTxs).toString();

            assertEquals("should match transaction root hash", testCase.getValue(), actualHash);
        }
    }

    // https://etherscan.io/getRawTx?tx=0xde78fe4a45109823845dc47c9030aac4c3efd3e5c540e229984d6f7b5eb4ec83
    @Test
    public void ethereumTransactionEIP1559DecoderTest() {
        // Test 1: Decoded tx should be as expected - same tx as linked above, just without access list
        var actualTx = EthereumTransactionDecoder.decode("0x02f8c60183012ec786023199fa3df88602e59652e99b8303851d9400000000003b3cc22af3ae1eac0440bcee416b4080b8530100d5a0afa68dd8cb83097765263adad881af6eed479c4a33ab293dce330b92aa52bc2a7cd3816edaa75f890b00000000000000000000000000000000000000000000007eb2e82c51126a5dde0a2e2a52f701c080a020d7f34682e1c2834fcb0838e08be184ea6eba5189eda34c9a7561a209f7ed04a07c63c158f32d26630a9732d7553cfc5b16cff01f0a72c41842da693821ccdfcb");
        var expectedTx = new EthereumTransaction(
                1L,
                EthereumTransactionUtils.getToAddressFromString("0x00000000003b3cc22aF3aE1EAc0440BcEe416B40"),
                Numeric.toBigInt("12EC7"),
                Numeric.toBigInt("0x03851d"),
                Numeric.toBigInt("0x023199fa3df8"),
                Numeric.toBigInt("0x02e59652e99b"),
                BigInteger.ZERO,
                EthereumTransactionUtils.getDataFromString("0x0100d5a0afa68dd8cb83097765263adad881af6eed479c4a33ab293dce330b92aa52bc2a7cd3816edaa75f890b00000000000000000000000000000000000000000000007eb2e82c51126a5dde0a2e2a52f701"),
                null);

        assertArrayEquals(expectedTx.messageToSign(), actualTx.messageToSign());

        // similar test adding the signature and comparing ids
        var expectedTx2 = new EthereumTransaction(expectedTx,
            new SignatureSecp256k1(
                new BigInteger("1b", 16),
                new BigInteger("20d7f34682e1c2834fcb0838e08be184ea6eba5189eda34c9a7561a209f7ed04", 16),
                new BigInteger("7c63c158f32d26630a9732d7553cfc5b16cff01f0a72c41842da693821ccdfcb", 16)
            )
        );

        assertEquals(expectedTx2.id(), actualTx.id());


        // Test 2: Access list is not allowed in Ethereum Transaction, should throw exception
        assertThrows("Test2: Exception during tx decoding expected", IllegalArgumentException.class,
                () -> EthereumTransactionDecoder.decode("0x02f9040c0183012ec786023199fa3df88602e59652e99b8303851d9400000000003b3cc22af3ae1eac0440bcee416b4080b8530100d5a0afa68dd8cb83097765263adad881af6eed479c4a33ab293dce330b92aa52bc2a7cd3816edaa75f890b00000000000000000000000000000000000000000000007eb2e82c51126a5dde0a2e2a52f701f90344f9024994a68dd8cb83097765263adad881af6eed479c4a33f90231a00000000000000000000000000000000000000000000000000000000000000004a0745448ebd86f892e3973b919a6686b32d8505f8eb2e02df5a36797f187adb881a00000000000000000000000000000000000000000000000000000000000000003a00000000000000000000000000000000000000000000000000000000000000011a0a580422a537c1b63e41b8febf02c6c28bef8713a2a44af985cc8d4c2b24b1c86a091e3d6ffd1390da3bfbc0e0875515e89982841b064fcda9b67cffc63d8082ab6a091e3d6ffd1390da3bfbc0e0875515e89982841b064fcda9b67cffc63d8082ab8a0bf9ee777cf4683df01da9dfd7aeab60490278463b1d516455d67d23c750f96dca00000000000000000000000000000000000000000000000000000000000000012a0000000000000000000000000000000000000000000000000000000000000000fa00000000000000000000000000000000000000000000000000000000000000010a0a580422a537c1b63e41b8febf02c6c28bef8713a2a44af985cc8d4c2b24b1c88a0bd9bbcf6ef1c613b05ca02fcfe3d4505eb1c5d375083cb127bda8b8afcd050fba06306683371f43cb3203ee553ce8ac90eb82e4721cc5335d281e1e556d3edcdbca00000000000000000000000000000000000000000000000000000000000000013a0bd9bbcf6ef1c613b05ca02fcfe3d4505eb1c5d375083cb127bda8b8afcd050f9a00000000000000000000000000000000000000000000000000000000000000014f89b94ab293dce330b92aa52bc2a7cd3816edaa75f890bf884a0000000000000000000000000000000000000000000000000000000000000000ca00000000000000000000000000000000000000000000000000000000000000008a00000000000000000000000000000000000000000000000000000000000000006a00000000000000000000000000000000000000000000000000000000000000007f85994c02aaa39b223fe8d0a0e5c4f27ead9083c756cc2f842a051c9df7cdd01b5cb5fb293792b1e67ec1ac1048ae7e4c7cf6cf46883589dfbd4a03c679e5fc421e825187f885e3dcd7f4493f886ceeb4930450588e35818a32b9c80a020d7f34682e1c2834fcb0838e08be184ea6eba5189eda34c9a7561a209f7ed04a07c63c158f32d26630a9732d7553cfc5b16cff01f0a72c41842da693821ccdfcb"));
    }

    // https://etherscan.io/getRawTx?tx=0x2513ac39d04c0d88611e509666c5da0f0d6dce367cf29dd6bdb339a62d541251
    @Test
    public void ethereumTransactionEip155LegacyDecoderTest() {
        // Test 1: Decoded tx should be as expected - same tx as linked above
        var actualTx = EthereumTransactionDecoder.decode("0xf86b3f850f224d4a008257a09461122d8e2d464bad53e054c965110de321233bc6870110d9316ec0008026a0a4e306691e16bbaa67faafeb00d81431b13194dcb39d97cf7dde47a6874d92d8a03b3b6a4500ff90d25022616d237de8559ab79cb294905cf79cadcdf076b50a2a");
        var expectedTx = new EthereumTransaction(
                1L,
                EthereumTransactionUtils.getToAddressFromString("0x61122d8e2d464bad53e054c965110de321233bc6"),
                Numeric.toBigInt("0x3f"),
                Numeric.toBigInt("0xf224d4a00"),
                Numeric.toBigInt("0x57a0"),
                Numeric.toBigInt("0x110d9316ec000"),
                new byte[]{},
                null);

        assertArrayEquals(expectedTx.messageToSign(), actualTx.messageToSign());

        // similar test adding the real signature (taken from original tx) and comparing ids
        SignatureSecp256k1 signature = new SignatureSecp256k1(
                new BigInteger("1c", 16),
                new BigInteger("a4e306691e16bbaa67faafeb00d81431b13194dcb39d97cf7dde47a6874d92d8", 16),
                new BigInteger("3b3b6a4500ff90d25022616d237de8559ab79cb294905cf79cadcdf076b50a2a", 16)
        );
        var expectedTx2 = new EthereumTransaction(expectedTx, signature);

        assertEquals(expectedTx2.id(), actualTx.id());
    }

    // https://etherscan.io/getRawTx?tx=0xdac06fbbe4389a99ac696c5d603a8e5c32ee540c519ffe4472981f38d3107d2c
    @Test
    public void ethereumTransactionLegacyDecoderTest() {
        // Test 1: Decoded tx should be as expected - same tx as linked above, just without access list
        var actualTx = EthereumTransactionDecoder.decode("0xf86e82b7ea850ba43b740083015f9094f8b786fdb1193f4a4cc2a8bc24a7cf47a7ce1cc28729b1c422c243fe801ba00c46734fb4d40be33b2b9bf7c367dc87097cfd4c75189b47c2148740ef031cb0a0726dad551589d504fdf065029a17b14523fbcc526a05c0146b71afec5394563b");
        var expectedTx = new EthereumTransaction(
                EthereumTransactionUtils.getToAddressFromString("0xf8b786fdb1193f4a4cc2a8bc24a7cf47a7ce1cc2"),
                Numeric.toBigInt("0xb7ea"),
                Numeric.toBigInt("0xba43b7400"),
                Numeric.toBigInt("0x15f90"),
                Numeric.toBigInt("0x29b1c422c243fe"),
                new byte[]{},
                null);

        assertArrayEquals(expectedTx.messageToSign(), actualTx.messageToSign());

        // similar test adding the signature and comparing ids
        var expectedTx2 = new EthereumTransaction(expectedTx,
                new SignatureSecp256k1(
                    new BigInteger("1b", 16),
                    new BigInteger("0c46734fb4d40be33b2b9bf7c367dc87097cfd4c75189b47c2148740ef031cb0", 16),
                    new BigInteger("726dad551589d504fdf065029a17b14523fbcc526a05c0146b71afec5394563b", 16)
                )
        );

        assertEquals(expectedTx2.id(), actualTx.id());
    }
}
