package com.horizen.account.receipt;

import com.horizen.evm.TrieHasher;
import com.horizen.utils.BytesUtils;
import org.junit.Test;
import org.web3j.utils.Numeric;
import scorex.crypto.hash.Keccak256;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.horizen.account.receipt.EthereumLogsTestJava.createTestEthereumLogJava;
import static java.util.Map.entry;
import static org.junit.Assert.*;

public class EthereumReceiptJavaTest {

    // test vectors encoded by go lib have been produced via:
    //     libevm/lib/service_hash_test.go


    static public EthereumReceiptJava createTestEthereumReceiptJava() {
        List<EthereumLogJava> logs = new ArrayList<>();
        logs.add(createTestEthereumLogJava());
        logs.add(createTestEthereumLogJava());

        EthereumReceiptJava receipt = new EthereumReceiptJava(
                EthereumReceiptJava.ReceiptTxType.DynamicFeeTxType.ordinal(),
                1,
                BigInteger.valueOf(1000),
                logs,
                new byte[256]
        );
        receipt.setGasUsed(BigInteger.valueOf(123456));
        receipt.setTransactionHash((byte[]) Keccak256.hash("txhash".getBytes()));
        receipt.setTransactionIndex(33);
        receipt.setBlockHash((byte[]) Keccak256.hash("blockhash".getBytes()));
        receipt.setBlockNumber(22);
        receipt.setContractAddress(BytesUtils.fromHexString("1122334455667788990011223344556677889900"));
        return receipt;
    }

    @Test
    public void receiptSimpleEncodeDecodeLegacyTest() {

        EthereumReceiptJava receipt = new EthereumReceiptJava(
                EthereumReceiptJava.ReceiptTxType.LegacyTxType.ordinal(),
                1,
                BigInteger.valueOf(123456),
                new ArrayList<>(),
                new byte[0]
        );

        byte[] encodedReceipt = EthereumReceiptJava.rlpEncode(receipt);
        //System.out.println(BytesUtils.toHexString(encodedReceipt));

        // read what you write
        EthereumReceiptJava decodedReceipt = EthereumReceiptJava.rlpDecode(encodedReceipt);
        //System.out.println(decodedReceipt);

        assertEquals(receipt.getTxType(), decodedReceipt.getTxType());
        assertEquals(receipt.getStatus(), decodedReceipt.getStatus());
        assertEquals(receipt.getCumulativeGasUsed().toString(), decodedReceipt.getCumulativeGasUsed().toString());
    }

    @Test
    public void receiptSimpleEncodeDecodeType1Test() {

        EthereumReceiptJava receipt = new EthereumReceiptJava(
                EthereumReceiptJava.ReceiptTxType.AccessListTxType.ordinal(),
                1,
                BigInteger.valueOf(1000),
                new ArrayList<>(),
                new byte[256]
        );

        byte[] encodedReceipt = EthereumReceiptJava.rlpEncode(receipt);
        //System.out.println(BytesUtils.toHexString(encodedReceipt));

        // read what you write
        byte[] dataBytes = encodedReceipt;
        EthereumReceiptJava decodedReceipt = EthereumReceiptJava.rlpDecode(dataBytes);
        //System.out.println(decodedReceipt);

        assertEquals(receipt.getTxType(), decodedReceipt.getTxType());
        assertEquals(receipt.getStatus(), decodedReceipt.getStatus());
        assertEquals(receipt.getCumulativeGasUsed().toString(), decodedReceipt.getCumulativeGasUsed().toString());
    }

    @Test
    public void receiptSimpleEncodeDecodeType2Test() {

        EthereumReceiptJava receipt = new EthereumReceiptJava(
                EthereumReceiptJava.ReceiptTxType.DynamicFeeTxType.ordinal(),
                1,
                BigInteger.valueOf(1000),
                new ArrayList<>(),
                new byte[256]
        );

        byte[] encodedReceipt = EthereumReceiptJava.rlpEncode(receipt);
        //System.out.println(BytesUtils.toHexString(encodedReceipt));

        // read what you write
        byte[] dataBytes = encodedReceipt;
        EthereumReceiptJava decodedReceipt = EthereumReceiptJava.rlpDecode(dataBytes);
        //System.out.println(decodedReceipt);

        assertEquals(receipt.getTxType(), decodedReceipt.getTxType());
        assertEquals(receipt.getStatus(), decodedReceipt.getStatus());
        assertEquals(receipt.getCumulativeGasUsed().toString(), decodedReceipt.getCumulativeGasUsed().toString());
    }

    @Test
    public void receiptDecodeGoEncodedType1NoLogsTest() {

        String dataStrType1 = "01f90108018203e8b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";
        byte[] dataBytes = BytesUtils.fromHexString(dataStrType1);

        EthereumReceiptJava decodedReceipt = EthereumReceiptJava.rlpDecode(dataBytes);
        //System.out.println(decodedReceipt);
        assertEquals(decodedReceipt.getTxType(), EthereumReceiptJava.ReceiptTxType.AccessListTxType);

        // encode and check we are the same as the original
        byte[] encodedReceipt = EthereumReceiptJava.rlpEncode(decodedReceipt);
        System.out.println(BytesUtils.toHexString(encodedReceipt));
        assertEquals(BytesUtils.toHexString(encodedReceipt), dataStrType1);
    }

    @Test
    public void receiptDecodeGoEncodedType2NoLogsTest() {

        String dataStrType2 = "02f9010901830b90f0b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";
        byte[] dataBytes = BytesUtils.fromHexString(dataStrType2);

        EthereumReceiptJava decodedReceipt = EthereumReceiptJava.rlpDecode(dataBytes);
        //System.out.println(decodedReceipt);
        assertEquals(decodedReceipt.getTxType(), EthereumReceiptJava.ReceiptTxType.DynamicFeeTxType);

        // encode and check we are the same as the original
        byte[] encodedReceipt = EthereumReceiptJava.rlpEncode(decodedReceipt);
        System.out.println(BytesUtils.toHexString(encodedReceipt));
        assertEquals(BytesUtils.toHexString(encodedReceipt), dataStrType2);
    }

    @Test
    public void receiptDecodeGoEncodedLegacyNoLogsTest() {

        String dataStrType0 = "f9010801820bb8b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";
        byte[] dataBytes = BytesUtils.fromHexString(dataStrType0);

        EthereumReceiptJava decodedReceipt = EthereumReceiptJava.rlpDecode(dataBytes);
        System.out.println(decodedReceipt);
        assertEquals(decodedReceipt.getTxType(), EthereumReceiptJava.ReceiptTxType.LegacyTxType);

        // encode and check we are the same as the original
        byte[] encodedReceipt = EthereumReceiptJava.rlpEncode(decodedReceipt);
        System.out.println(BytesUtils.toHexString(encodedReceipt));
        assertEquals(BytesUtils.toHexString(encodedReceipt), dataStrType0);
    }

    @Test
    public void receiptDecodeGoEncodedLegacyWithLogsTest() {

        String dataStrType0 = "f901458080b9010000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000400000000000000000000000000000000000000000000000080000000000000000000000002000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000f83ef83c941122334455667788990011223344556677889900e1a040eed0325a12c6c6af8db2ea05450bfe21d6343b6fe955bff65045b67d9d5fe684aabbccdd";
        byte[] dataBytes = BytesUtils.fromHexString(dataStrType0);

        EthereumReceiptJava decodedReceipt = EthereumReceiptJava.rlpDecode(dataBytes);
        System.out.println(decodedReceipt);
        assertEquals(decodedReceipt.getTxType(), EthereumReceiptJava.ReceiptTxType.LegacyTxType);

        // encode and check we are the same as the original
        byte[] encodedReceipt = EthereumReceiptJava.rlpEncode(decodedReceipt);
        System.out.println(BytesUtils.toHexString(encodedReceipt));
        assertEquals(BytesUtils.toHexString(encodedReceipt), dataStrType0);
    }

    // compatible with analogous go code in libevm/lib/service_hash_test.go
    private EthereumReceiptJava[] generateReceipt(int count) {

        var receipts = new EthereumReceiptJava[count];
        for (int i = 0; i < receipts.length; i++) {
            int status = 1;
            if (i%7 == 0) {
                // mark a number of receipts as failed
                status = 0;
            }

            int txType = i%3;

            var cumGas = BigInteger.valueOf(i).multiply(BigInteger.TEN.pow(3));
            //System.out.println("cumGas =" + cumGas.toString());
            byte[] logsBloom = new byte[256];
            List<EthereumLogJava> logs =  new ArrayList<>();
            receipts[i] = new EthereumReceiptJava(txType, status, cumGas, logs, logsBloom);
            //System.out.println("i=" + i + receipts[i].toString());
        }
        return receipts;
    }

    @Test
    public void ethereumReceiptRootHashTest() {
        // source of these hashes: TestHashRoot() at libevm/lib/service_hash_test.go
        final var testCases =
                Map.ofEntries(
                      entry(0, "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"),
                      entry(1, "0x703c035562d8e37c66f2f9461219b45c710e59c8d0d234f6b062107de627758c"),
                      entry(10, "0xfebc2ff67381b064a8b194c9e6a3e771e0daf4a92f31e02946d01ae36bf1122a"),
                      entry(1000, "0xf76017831e585d894a579497a9b4054379bef06fedbe2e3e11fed842c57b7d72"),
                      entry(126, "0x4cd93fc943ce3de24a4c60ca7b31065ea8a52e21832e454d6adae4b2ac2acc19"),
                      entry(127, "0xc65b5ec3b081e44a8ba7da8163749539643d5058b7a2e2cfd628e401b5863756"),
                      entry(128, "0x6aa1c5f94dedae352ae4c983eac84921b6b4f66b8f522483b54025b86bfd40c1"),
                      entry(129, "0xbd9a3d3db6252d968940aa62a3bb856f8537b775eb675566be49eeafc4cfd0fc"),
                      entry(130, "0x62008462d610bf97e84aa1bc8d8b496777530d1cbf876e627ebab7f3a455d857"),
                      entry(2, "0x012b665bb84f73c46a83f40969b229a5cbd33f964c0fc3fb6b8114371df42f30"),
                      entry(3, "0x34f2a7f429e9a46e8b5b2dfcb9475a83d9fe3a320726e32ed47a1b97c3d9dc86"),
                      entry(4, "0x64f4f73bbc52fe58bdfb41f444a285c46db65ebfd882f834851070379abf7c37"),
                      entry(51, "0xf0f45d15b0f211d0e10d0b5b0ac0fdcb50d66a7d6b5413f435dcf89581aaaccb"),
                      entry(765, "0x204abb0c3e6d4f24a3a6f7c4f90b7185ab618426d05396714d7489c02cd9d7b5")
                );
        for (var testCase : testCases.entrySet()) {
            final var receipts = generateReceipt(testCase.getKey());
            final var rlpReceipts = Arrays.stream(receipts).map(
                    r -> EthereumReceiptJava.rlpEncode(r)).toArray(byte[][]::new);
            final var actualHash = Numeric.toHexString(TrieHasher.Root(rlpReceipts));
            /*
            System.out.println("i: " + testCase.getKey() +
                    ", value: " + testCase.getValue().toString() +
                    ", actual: "+ actualHash.toString());
             */
            assertEquals("should match transaction root hash", testCase.getValue(), actualHash);
        }
    }

    @Test
    public void receiptSimpleSerializeUnserialize() {

        List<EthereumLogJava> logs = new ArrayList<>();
        logs.add(createTestEthereumLogJava());
        logs.add(createTestEthereumLogJava());

        EthereumReceiptJava receipt = new EthereumReceiptJava(
                EthereumReceiptJava.ReceiptTxType.DynamicFeeTxType.ordinal(),
                1,
                BigInteger.valueOf(1000),
                //new ArrayList<>(),
                logs,
                new byte[256]
        );
        receipt.setGasUsed(BigInteger.valueOf(123456));
        receipt.setTransactionHash((byte[]) Keccak256.hash("txhash".getBytes()));
        receipt.setTransactionIndex(33);
        receipt.setBlockHash((byte[]) Keccak256.hash("blockhash".getBytes()));
        receipt.setBlockNumber(22);
        receipt.setContractAddress(BytesUtils.fromHexString("1122334455667788990011223344556677889900"));

        String r1 = receipt.toString(true);
        System.out.println(r1);

        byte[] serializedBytes = EthereumReceiptJavaSerializer.getSerializer().toBytes(receipt);
        System.out.println(BytesUtils.toHexString(serializedBytes));

        EthereumReceiptJava decodedReceipt = EthereumReceiptJavaSerializer.getSerializer().parseBytes(serializedBytes);
        String r2 = decodedReceipt.toString(true);
        System.out.println(r2);

        assertEquals(r1, r2);

    }
}
