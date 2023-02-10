package com.horizen.account.transaction;

import com.google.common.primitives.Bytes;
import com.horizen.account.fixtures.EthereumTransactionFixture;
import com.horizen.account.utils.EthereumTransactionDecoder;
import com.horizen.account.utils.RlpStreamDecoder;
import com.horizen.account.utils.RlpStreamEncoder;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.BytesUtils;
import org.bouncycastle.util.Arrays;
import org.junit.Ignore;
import org.junit.Test;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import sparkz.util.ByteArrayBuilder;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.VLQByteBufferReader;
import sparkz.util.serialization.VLQByteBufferWriter;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class EthereumTransactionRlpStreamCodecTest implements EthereumTransactionFixture {

    @Test
    public void rlpCodecEthTxLegacy() {
        EthereumTransaction ethTx = getEoa2EoaLegacyTransaction();
        checkEthTxEncoding(ethTx);
    }

    @Test
    public void rlpCodecEthTxLegacyEIP155() {
        EthereumTransaction ethTx = getEoa2EoaEip155LegacyTransaction();
        checkEthTxEncoding(ethTx);
    }

    @Test
    public void rlpCodecEthTxEIP1559() {
        EthereumTransaction ethTx = getEoa2EoaEip1559Transaction();
        checkEthTxEncoding(ethTx);
    }

    @Test
    public void rlpCodecContractCallEthTx() {
        EthereumTransaction ethTx = getContractCallEip155LegacyTransaction();
        checkEthTxEncoding(ethTx);
    }

    @Test
    public void rlpCodecContractDeploymentEthTx() {
        EthereumTransaction ethTx = getContractDeploymentEip1559Transaction();
        checkEthTxEncoding(ethTx);
    }

    @Test
    public void rlpCodecBigDataSizeEthTx() {
        EthereumTransaction ethTx = getBigDataTransaction(50000000, BigInteger.valueOf(100000000));
        checkEthTxEncoding(ethTx);
    }

    @Test
    public void rlpCodecManyEthTx() {
        EthereumTransaction ethTx1 = getEoa2EoaEip1559Transaction();
        EthereumTransaction ethTx2 = getEoa2EoaLegacyTransaction();
        EthereumTransaction ethTx3 = getEoa2EoaEip155LegacyTransaction();

        // encode same txs with stream writer
        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        ethTx1.encode(true, writer);
        ethTx2.encode(true, writer);
        ethTx3.encode(true, writer);
        byte[] joinedStreamEncodedBytes = writer.result().toBytes();

        // decode using stream reader
        Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(joinedStreamEncodedBytes));
        var ethTxDecoded1 = EthereumTransactionDecoder.decode(reader);
        var ethTxDecoded2 = EthereumTransactionDecoder.decode(reader);
        var ethTxDecoded3 = EthereumTransactionDecoder.decode(reader);

        // check we consumed all bytes
        assertEquals(reader.remaining(), 0);

        // check we have decoded properly
        assertEquals(ethTx1, ethTxDecoded1);
        assertEquals(ethTx2, ethTxDecoded2);
        assertEquals(ethTx3, ethTxDecoded3);
    }


    private void checkEthTxEncoding(EthereumTransaction ethTx) {

        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        ethTx.encode(true, writer);
        byte[] encodedBytes = writer.result().toBytes();

        // read what has been written
        Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(encodedBytes));
        var ethTxDecoded = EthereumTransactionDecoder.decode(reader);
        assertEquals(ethTx, ethTxDecoded);
    }

    @Test
    public void rlpEthTxWithTrailingBytes() {
        EthereumTransaction ethTx = getEoa2EoaLegacyTransaction();

        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        ethTx.encode(true, writer);
        byte[] encodedBytes = writer.result().toBytes();

        byte[] encodedBytesWithTrailingData =
                Bytes.concat(encodedBytes, BytesUtils.fromHexString("badcaffe"));

        Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(encodedBytesWithTrailingData));
        assertThrows(
                "Exception expected, because trailing data are not RLP encoded",
                java.lang.RuntimeException.class,
                () ->
                {
                    while (reader.remaining() > 0) {
                        EthereumTransactionDecoder.decode(reader);
                    }
                }
        );
    }

    @Test
    public void rlpEthTxWithTrimmedBytes() {
        EthereumTransaction ethTx = getEoa2EoaLegacyTransaction();

        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        ethTx.encode(true, writer);
        byte[] encodedBytes = writer.result().toBytes();

        byte[] encodedBytesWithTrimmedData =
                Arrays.copyOf(encodedBytes, encodedBytes.length-2);

        Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(encodedBytesWithTrimmedData));
        assertThrows(
                "Exception expected, because data are not a complete RLP encoded array",
                java.lang.RuntimeException.class,
                () ->
                {
                    while (reader.remaining() > 0) {
                        EthereumTransactionDecoder.decode(reader);
                    }
                }
        );
    }


    private RlpList getEip1559TxInternalRlpList(EthereumTransaction ethTx) {
        assertTrue(ethTx.isEIP1559());
        byte[] encodedBytes = ethTx.encode(true);
        byte[] encodedTx = java.util.Arrays.copyOfRange(encodedBytes, 1, encodedBytes.length);
        Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(encodedTx));
        return (RlpList) RlpStreamDecoder.decode(reader).getValues().get(0);
    }

    private RlpList getLegacyTxInternalRlpList(EthereumTransaction ethTx) {
        assertTrue(ethTx.isEIP155());
        byte[] encodedTx = ethTx.encode(true);
        Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(encodedTx));
        return (RlpList) RlpStreamDecoder.decode(reader).getValues().get(0);
    }

    @Test
    public void rlpEthTxEIP1559IllFormatted() {
        EthereumTransaction ethTx = getEoa2EoaEip1559Transaction();
        RlpList ethTxList = getEip1559TxInternalRlpList(ethTx);

        // add an invalid item to the eth rlp list
        ethTxList.getValues().add(RlpString.create("Hello"));

        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(ethTxList, writer);
        byte[] tamperedBytes1 = writer.toBytes();
        byte[] tamperedBytes = ByteBuffer.allocate(tamperedBytes1.length + 1)
                .put(ethTx.version())
                .put(tamperedBytes1)
                .array();

        Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(tamperedBytes));
        assertThrows(
                "Exception expected, because data are a decoded invalid RLP list",
                IllegalArgumentException.class,
                () -> EthereumTransactionDecoder.decode(reader)
        );

    }

    @Test
    public void rlpEthTxLegacyIllFormatted() {
        EthereumTransaction ethTx = getEoa2EoaEip155LegacyTransaction();
        RlpList ethTxList = getLegacyTxInternalRlpList(ethTx);

        // add an invalid item to the eth rlp list
        ethTxList.getValues().add(RlpString.create("Hello"));

        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(ethTxList, writer);
        byte[] tamperedBytes = writer.toBytes();

        Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(tamperedBytes));
        assertThrows(
                "Exception expected, because data are a decoded invalid RLP list",
                IllegalArgumentException.class,
                () -> EthereumTransactionDecoder.decode(reader)
        );
    }

    // See: https://github.com/ethereum/tests/tree/develop/src/TransactionTestsFiller/ttWrongRLP

    // Badly RLP encoded transactions
    String[][] gethTestVectorsWrongRlp = {
            {"aCrashingRLP", "96dc24d6874a9b01e4a7b7e5b74db504db3731f764293769caef100f551efadf7d378a015faca6ae62ae30a9bf5e3c6aa94f58597edc381d0ec167fa0c84635e12a2d13ab965866ebf7c7aae458afedef1c17e08eb641135f592774e18401e0104f8e7f8e0d98e3230332e3133322e39342e31333784787beded84556c094cf8528c39342e3133372e342e31333982765fb840621168019b7491921722649cd1aa9608f23f8857d782e7495fb6765b821002c4aac6ba5da28a5c91b432e5fcc078931f802ffb5a3ababa42adee7a0c927ff49ef8528c3136322e3234332e34362e39829dd4b840e437a4836b77ad9d9ffe73ee782ef2614e6d8370fcf62191a6e488276e23717147073a7ce0b444d485fff5a0c34c4577251a7a990cf80d8542e21b95aa8c5e6cdd8e3230332e3133322e39342e31333788ffffffffa5aadb3a84556c095384556c0919"},
            {"aMaliciousRLP", "b8"},
            {"RLP_04_maxFeePerGas32BytesValue", "04f88601808477359400a0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff82520894095e7baea6a6c7c4c2dfeb977efac326af552d878080c080a05cbd172231fc0735e0fb994dd5b1a4939170a260b36f0427a8a80866b063b948a07c230f7f578dd61785c93361b9871c0706ebfa6d06e3f4491dc9558c5202ed36"},
            {"RLP_09_maxFeePerGas32BytesValue", "09f88601808477359400a0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff82520894095e7baea6a6c7c4c2dfeb977efac326af552d878080c080a05cbd172231fc0735e0fb994dd5b1a4939170a260b36f0427a8a80866b063b948a07c230f7f578dd61785c93361b9871c0706ebfa6d06e3f4491dc9558c5202ed36"},
            {"RLPAddressWithFirstZeros", "f86080018209489500095e7baea6a6c7c4c2dfeb977efac326af552d870a801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a0efffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"},
            {"RLPAddressWrongSize", "f866830ffdc50183adc05390fce5edbc8e2a8697c15331677e6ebf0b870ffdc5fffdc12c801ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"RLPElementIsListWhenItShouldntBe2", "f86bcc83646f6783676f64836361740182035294095e7baea6a6c7c4c2dfeb977efac326af552d870a801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a0efffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"},
            {"RLPElementIsListWhenItShouldntBe", "f8698001cc83646f6783676f648363617494095e7baea6a6c7c4c2dfeb977efac326af552d870a801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a0efffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"},
            {"RLPExtraRandomByteAtTheEnd", "f85280018207d0870b9331677e6ebf0a801ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a33ac4"},
            {"RLPHeaderSizeOverflowInt32", "ff0f0000000000005f030182520894b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a801ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"RLPTransactionGivenAsArray", "b85f800182035294095e7baea6a6c7c4c2dfeb977efac326af552d870a801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a0efffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"},
            {"TRANSCT_data_GivenAsList", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0ac255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT_gasLimit_GivenAsList", "f8610301c207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT_HeaderGivenAsArray_0", "b86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT_HeaderLargerThanRLP_0", "f86f03018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__RandomByteAtRLP_0", "f86ef103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__RandomByteAtRLP_1", "f86103018207d094b9ef4f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__RandomByteAtRLP_2", "f86103018207d094b94f5374fce5edbc8efe2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__RandomByteAtRLP_3", "f86103018207d094b94f5374fce5edbc8e2a8697c1533167ef7e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__RandomByteAtRLP_4", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a82554ef41ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__RandomByteAtRLP_5", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201ef554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__RandomByteAtRLP_6", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8cef804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__RandomByteAtRLP_7", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285efebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__RandomByteAtRLP_8", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44efb9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__RandomByteAtRLP_9", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887ef321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            // byte 33: rvalue is 34 byte long with two leading zeros [ a2 00 00 ...] (should be [ a0 .. ]
            {"TRANSCT_rvalue_Prefixed0000", "f863030182a7d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca2000098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT_rvalue_GivenAsList", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ce098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT_rvalue_TooLarge", "f86303018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca2ef3d98ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            // same as above for s value
            {"TRANSCT_svalue_Prefixed0000", "f863030182a7d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa200008887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            // byte 3: same as above for gas price
            {"RLPgasPriceWithFirstZeros", "f862808300000182a35294095e7baea6a6c7c4c2dfeb977efac326af552d870a801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a0efffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"},
            // byte 2: same as above for nonce
            {"RLPNonceWithFirstZeros", "f86384000000030182a35294095e7baea6a6c7c4c2dfeb977efac326af552d870a801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a0efffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"},
            // byte 28: same as above for value 10 [82 00 0a] (Should be [0a]
            {"RLPValueWithFirstZeros", "f861800182a35294095e7baea6a6c7c4c2dfeb977efac326af552d8782000a801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a0efffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"},
            // byte 4: gas limit is encoded as an array of size 5 with leading zeroes: [85 00 00 00 09 48] (Should be [82 09 48])
            {"RLPgasLimitWithFirstZeros", "f862800185000000a94894095e7baea6a6c7c4c2dfeb977efac326af552d870a801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a0efffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"},
            // Similar to RLPgasLimitWithFirstZeros
            {"TRANSCT_gasLimit_Prefixed0000", "f8630301840000a7d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            // byte 33: the r value is an array of 30 byte instead of 32.
            {"TRANSCT_rvalue_TooShort", "f85f030182a7d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441c9e921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT_svalue_GivenAsList", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4ae08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT_svalue_TooLarge", "f86303018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa2ef3d8887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT_to_GivenAsList", "f86103018207d0d4b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT_to_Prefixed0000", "f86303018207d0960000b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT_to_TooLarge", "f86303018207d096ef3db94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT_to_TooShort", "f85f03018207d0925374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__ZeroByteAtRLP_0", "f8600103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__ZeroByteAtRLP_1", "f86103018207d094b9004f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__ZeroByteAtRLP_2", "f86103018207d094b94f5374fce5edbc800e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__ZeroByteAtRLP_3", "f86103018207d094b94f5374fce5edbc8e2a8697c1533167007e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__ZeroByteAtRLP_4", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a825540041ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__ZeroByteAtRLP_5", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff92120100554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__ZeroByteAtRLP_6", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c00804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__ZeroByteAtRLP_7", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf28500ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__ZeroByteAtRLP_8", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c4400b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__ZeroByteAtRLP_9", "f861030182a7d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa0888700321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            {"TRANSCT__RandomByteAtTheEnd", "f86103018207d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3ef"},
            // s byte array length: 31 != 32 [9f ..] should be [a0 00 ..]
            {"tr201506052141PYTHON", "f8718439a6c38b88446cf2b7cba3be25847c4af09494a41e36344e8524318a21a527743b169f3a437b8684153aa6b4808189a0f5e5d736775026020ad30508a301eea73d2b096171e6ba17ac3d170f6863b55c9f5fe34e0580ce02b39acae5844a9da787ac7d1a4d97d6bfc53546ba2bc47880"},
    };
    // RLP encoded transactions which are decoded by us but yield eth tx objects not semantically valid
    String[][] gethTestVectorsSemanticValdationFailure = {
            // gas uint64 overflows
            {"TRANSCT_gasLimit_TooLarge", "f8810301a2ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff07d094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a8255441ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
    };
    // RLP encoded transactions which are correctly decoded by w3j (while they shouldn't) and yield semantically valid eth tx obj
    // Note: some of these testvectors has been modified for having a gas limit value above the intrinsic gas limit 21000, otherwise the semantic validity would fail
    String[][] gethTestVectorsNotFailingWithW3j = {
            // byte 31: a length expressed as two bytes with a leading 00: [b9 00 40] (should be [b8 40])
            {"RLPArrayLengthWithFirstZeros", "f8a20301830186a094b94f5374fce5edbc8e2a8697c15331677e6ebf0b0ab90040ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff1ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            // byte 0: the length of a list fits 1 byte but is encoded in two bytes with a leading 0 [f9 00 5f] (should be [f8 5f])
            {"RLPListLengthWithFirstZeros", "f9005f030182520894b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a801ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            // byte 2: a 0 is expressed not as an empty string but encoded explicitly [81 00] (should be [80])
            {"RLPIncorrectByteEncoding00", "f86081000182520894b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a801ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            // byte 2: a byte is expressed not as a string of value 0..0x7f but encoded explicitly as a string of length 1 contaning the value,[81 01] (should be [01])
            {"RLPIncorrectByteEncoding01", "f86081010182520894b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a801ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
            // the same as above, [81 7f] (should be [7f])
            {"RLPIncorrectByteEncoding127", "f860817f0182520894b94f5374fce5edbc8e2a8697c15331677e6ebf0b0a801ca098ff921201554726367d2be8c804a7ff89ccf285ebc57dff8ae4c44b9c19ac4aa08887321be575c8095f789dd4c743dfe42c1820f9231f98a962b210e3ac2452a3"},
    };


    @Test
    public void testGethDecodeRlpStream() {

        // check stream decoding correctly refuses to decode what w3j is not strictly handling
        String[][] supersetOfTestVectorsWrongRlp = Stream.of(gethTestVectorsWrongRlp, gethTestVectorsNotFailingWithW3j).flatMap(Stream::of)
                .toArray(String[][]::new);

        for (String[] strings : supersetOfTestVectorsWrongRlp) {
            byte[] b = BytesUtils.fromHexString(strings[1]);
            Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(b));
            try {
                EthereumTransaction ethTx = EthereumTransactionDecoder.decode(reader);
                //ethTx.semanticValidity();
                if (reader.remaining() != 0)
                    throw new IllegalArgumentException("Stream is not consumed, we have " + reader.remaining() + "bytes left to read");
                System.out.println(strings[0] + "--->" + ethTx.toString());
                fail("Should not succeed");
            } catch (Exception e) {
                System.out.println(strings[0] + "--->" + e.getMessage());
            }
        }
    }


    @Test
    public void testGethDecodeRlpSemanticValidationStream() {

        for (String[] strings : gethTestVectorsSemanticValdationFailure) {
            byte[] b = BytesUtils.fromHexString(strings[1]);
            Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(b));
            try {
                EthereumTransaction ethTx = EthereumTransactionDecoder.decode(reader);
                ethTx.semanticValidity();
                System.out.println(strings[0] + "--->" + ethTx);
                fail("Should not succeed");

            } catch (Exception e) {
                System.out.println(strings[0] + "--->" + e.getMessage());
            }
        }
    }

    @Test
    public void checkUnsignedTx() {
        // https://github.com/ethereumbook/ethereumbook/blob/develop/code/web3js/raw_tx/raw_tx_demo.js
        byte[] b = BytesUtils.fromHexString("e6808609184e72a0008303000094b0920c523d582040f2bcb1bd7fb1c7c1ecebdb3480801c8080");
        Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(b));
        EthereumTransaction ethTx = EthereumTransactionDecoder.decode(reader);
        assertFalse(ethTx.isSigned());

        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        ethTx.encode(true, writer);
        byte[] result = writer.toBytes();
        assertEquals(BytesUtils.toHexString(b), BytesUtils.toHexString(result));
    }
}
