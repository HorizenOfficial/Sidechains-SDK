package com.horizen.account.transaction;

import com.google.common.primitives.Bytes;
import com.horizen.account.fixtures.EthereumTransactionFixture;
import com.horizen.account.utils.EthereumTransactionDecoder;
import com.horizen.utils.BytesUtils;
import org.bouncycastle.util.Arrays;
import org.junit.Ignore;
import org.junit.Test;
import sparkz.util.ByteArrayBuilder;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.VLQByteBufferReader;
import sparkz.util.serialization.VLQByteBufferWriter;

import java.math.BigInteger;
import java.nio.ByteBuffer;
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

        // encode txs in w3j way and concatenate them
        byte[] encodedBytes1 = ethTx1.encode(true);
        byte[] encodedBytes2 = ethTx2.encode(true);
        byte[] encodedBytes3 = ethTx3.encode(true);

        byte[] joinedEncodedBytes = Bytes.concat(encodedBytes1, encodedBytes2, encodedBytes3);

        // encode same txs with stream writer
        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        ethTx1.encode(true, writer);
        ethTx2.encode(true, writer);
        ethTx3.encode(true, writer);
        byte[] joinedStreamEncodedBytes = writer.result().toBytes();

        // check resulting bytes are the same with both encoding strategies
        assertArrayEquals(joinedEncodedBytes, joinedStreamEncodedBytes);

        // decode using stream reader
        Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(joinedEncodedBytes));
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
        // use w3j way
        byte[] encodedBytes1 = ethTx.encode(true);

        // use a stream
        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        ethTx.encode(true, writer);
        byte[] encodedBytes2 = writer.result().toBytes();

        // check we have the same results
        assertArrayEquals(encodedBytes1, encodedBytes2);

        // do both decoding ways and check the results are as expected
        Reader reader1 = new VLQByteBufferReader(ByteBuffer.wrap(encodedBytes1));
        var ethTxDecoded1 = EthereumTransactionDecoder.decode(reader1);

        var ethTxDecoded2 = EthereumTransactionDecoder.decode(encodedBytes2);

        assertEquals(ethTxDecoded1, ethTxDecoded2);
        assertEquals(ethTx, ethTxDecoded1);
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


    @Test
    @Ignore
    public void checkPerfStream() {
        var transactionList = getTransactionList(50000);

        long startTime = System.nanoTime();
        for (EthereumTransaction tx:transactionList) {
            tx.encode(true);
        }

        long stopTime = System.nanoTime();
        System.out.println("RlpEncoding      : " + (stopTime - startTime));

        long startTime2 = System.nanoTime();
        for (EthereumTransaction tx:transactionList) {
            VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
            tx.encode(true, writer);
        }
        long stopTime2 = System.nanoTime();
        System.out.println("RlpStreamEncoding: " + (stopTime2 - startTime2));
    }
}
