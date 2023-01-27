package com.horizen.account.transaction;

import com.google.common.primitives.Bytes;
import com.horizen.account.event.EthereumEvent;
import com.horizen.account.fixtures.EthereumTransactionFixture;
import com.horizen.account.utils.EthereumTransactionDecoder;
import com.horizen.account.utils.RlpStreamDecoder;
import com.horizen.utils.BytesUtils;
import org.bouncycastle.util.Arrays;
import org.junit.Test;
import org.web3j.abi.datatypes.Address;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.utils.Numeric;
import scorex.util.ByteArrayBuilder;
import scorex.util.serialization.Reader;
import scorex.util.serialization.VLQByteBufferReader;
import scorex.util.serialization.VLQByteBufferWriter;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import static org.junit.Assert.*;

public class EthereumTransactionRlpStreamCodecTest implements EthereumTransactionFixture {





    @Test
    public void rlpCodecEmptyData() {
        byte[] encodedBytes = new byte[]{};
        Reader r = new VLQByteBufferReader(ByteBuffer.wrap(encodedBytes));
        RlpList rlpList = RlpStreamDecoder.decode(r);
        assertTrue(rlpList.getValues().isEmpty());
    }

    @Test
    public void rlpCodecNullReader() {
        RlpList rlpList = RlpStreamDecoder.decode(null);
        assertTrue(rlpList.getValues().isEmpty());
    }

    @Test
    public void rlpCodecEmptyString() {
        // the empty string ('null') = [ 0x80 ]
        RlpString rlpString = RlpString.create("");
        byte[] encodedBytes = RlpEncoder.encode(rlpString);
        assertEquals(BytesUtils.toHexString(encodedBytes), "80");

        Reader r = new VLQByteBufferReader(ByteBuffer.wrap(encodedBytes));
        RlpList rlpList = RlpStreamDecoder.decode(r);
        var emptyString = (RlpString)rlpList.getValues().get(0);
        checkRlpString(emptyString, "");
    }

    @Test
    public void rlpCodecEmptyList() {
        // the empty list = [ 0xc0 ]
        RlpList rlpEmptyList = new RlpList(new ArrayList<>());
        byte[] encodedBytes = RlpEncoder.encode(rlpEmptyList);
        assertEquals(BytesUtils.toHexString(encodedBytes), "c0");

        Reader r = new VLQByteBufferReader(ByteBuffer.wrap(encodedBytes));
        RlpList rlpList = RlpStreamDecoder.decode(r);
        var emptyList = (RlpList)rlpList.getValues().get(0);
        assertTrue(emptyList.getValues().isEmpty());
    }


    @Test
    public void rlpCodecZero() {

        // the integer 0 = [ 0x80 ]
        RlpString rlpInt = RlpString.create(0);
        byte[] encodedBytes = RlpEncoder.encode(rlpInt);
        assertEquals(BytesUtils.toHexString(encodedBytes), "80");

        Reader r = new VLQByteBufferReader(ByteBuffer.wrap(encodedBytes));
        RlpList rlpList = RlpStreamDecoder.decode(r);
        RlpString rlpDecodedString = (RlpString)rlpList.getValues().get(0);
        BigInteger rlpDecodedVal = rlpDecodedString.asPositiveBigInteger();
        assertEquals(rlpDecodedVal.longValueExact(), 0L);
    }


    @Test
    public void rlpCodecLong() {

        // the encoded integer 1024 ('\x04\x00') = [ 0x82, 0x04, 0x00 ]
        RlpString rlpInt = RlpString.create(1024L);
        byte[] encodedBytes = RlpEncoder.encode(rlpInt);
        assertEquals(BytesUtils.toHexString(encodedBytes), "820400");

        Reader r = new VLQByteBufferReader(ByteBuffer.wrap(encodedBytes));
        RlpList rlpList = RlpStreamDecoder.decode(r);
        RlpString rlpDecodedString = (RlpString)rlpList.getValues().get(0);
        BigInteger rlpDecodedVal = rlpDecodedString.asPositiveBigInteger();
        assertEquals(rlpDecodedVal.longValueExact(), 1024L);
    }

    @Test
    public void rlpCodecCatDogList() {

        // the list [ "cat", "dog" ] = [ 0xc8, 0x83, 'c', 'a', 't', 0x83, 'd', 'o', 'g' ]
        RlpList rlpEncodedList = new RlpList(new ArrayList<>());
        rlpEncodedList.getValues().add(RlpString.create("cat"));
        rlpEncodedList.getValues().add(RlpString.create("dog"));
        byte[] encodedBytes = RlpEncoder.encode(rlpEncodedList);
        assertEquals(BytesUtils.toHexString(encodedBytes), "c88363617483646f67");


        Reader r = new VLQByteBufferReader(ByteBuffer.wrap(encodedBytes));
        RlpList rlpList = RlpStreamDecoder.decode(r);
        RlpList rlpDecodedList = (RlpList)rlpList.getValues().get(0);
        var catRlpString = (RlpString)rlpDecodedList.getValues().get(0);
        var dogRlpString = (RlpString)rlpDecodedList.getValues().get(1);

        checkRlpString(catRlpString, "cat");
        checkRlpString(dogRlpString, "dog");
    }

    @Test
    public void rlpCodecSentence() {

        // https://en.wikipedia.org/wiki/Lorem_ipsum
        // the string "Lorem ipsum dolor sit amet, consectetur adipiscing elit!" =
        //   [ 0xb8, 0x38, 'L', 'o', 'r', 'e', 'm', ' ', ... , 'e', 'l', 'i', 't', '!' ]
        // The string length here is 56, a long string (1 byte beyond the limit of a short string)
        String sentence = "Lorem ipsum dolor sit amet, consectetur adipiscing elit!";
        RlpString rlpEncodedString = RlpString.create(sentence);
        byte[] encodedBytes = RlpEncoder.encode(rlpEncodedString);

        assertArrayEquals(
                encodedBytes,
                BytesUtils.fromHexString(
                        "b8384C6F72656D20697073756D20646F6C6F722073697420616D65742C20636F6E73656374657475722061646970697363696E6720656C697421")
        );

        Reader r = new VLQByteBufferReader(ByteBuffer.wrap(encodedBytes));
        RlpList rlpList = RlpStreamDecoder.decode(r);
        var decodedSentence = (RlpString)rlpList.getValues().get(0);
        checkRlpString(decodedSentence, sentence);
    }

    @Test
    public void rlpCodecConcatenatedSentences() {
        ArrayList<String> sentences = new ArrayList<>();
        sentences.add("Under the lying stone water does not flow");
        sentences.add("Every man is sociable until a cow invades his garden");

        RlpString rlpEncodedString1 = RlpString.create(sentences.get(0));
        byte[] encodedBytes1 = RlpEncoder.encode(rlpEncodedString1);

        RlpString rlpEncodedString2 = RlpString.create(sentences.get(1));
        byte[] encodedBytes2 = RlpEncoder.encode(rlpEncodedString2);

        byte[] joinedEcodedBytes = Bytes.concat(encodedBytes1, encodedBytes2);

        Reader r = new VLQByteBufferReader(ByteBuffer.wrap(joinedEcodedBytes));

        int count = 0;
        while (r.remaining() > 0) {
            RlpList rlpList = RlpStreamDecoder.decode(r);
            var decodedSentence = (RlpString) rlpList.getValues().get(0);
            checkRlpString(decodedSentence, sentences.get(count));
            count += 1;
        }

    }

    private void checkRlpString(RlpString rlpString, String s) {
        String hexStr = Numeric.cleanHexPrefix(rlpString.asString());
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < hexStr.length(); i+=2) {
            String str = hexStr.substring(i, i+2);
            output.append((char)Integer.parseInt(str, 16));
        }
        System.out.println(output);
        assertEquals(output.toString(), s);
    }

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

        // encode txs and concatenate them
        byte[] encodedBytes1 = ethTx1.encode(true);
        byte[] encodedBytes2 = ethTx2.encode(true);
        byte[] encodedBytes3 = ethTx3.encode(true);

        byte[] joinedEcodedBytes = Bytes.concat(encodedBytes1, encodedBytes2, encodedBytes3);

        // encode same txs with stream writer
        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        ethTx1.encode(true, writer);
        ethTx2.encode(true, writer);
        ethTx3.encode(true, writer);
        byte[] joinedStreamEcodedBytes = writer.result().toBytes();

        // check resulting bytes are the same with both encoding strategies
        assertArrayEquals(joinedEcodedBytes, joinedStreamEcodedBytes);

        // decode using stream reader
        Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(joinedEcodedBytes));
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
        byte[] encodedBytes1 = ethTx.encode(true);

        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        ethTx.encode(true, writer);
        byte[] encodedBytes2 = writer.result().toBytes();

        assertArrayEquals(encodedBytes1, encodedBytes2);

        Reader reader1 = new VLQByteBufferReader(ByteBuffer.wrap(encodedBytes1));
        var ethTxDecoded1 = EthereumTransactionDecoder.decode(reader1);

        Reader reader2 = new VLQByteBufferReader(ByteBuffer.wrap(encodedBytes2));
        var ethTxDecoded2 = EthereumTransactionDecoder.decode(reader2);

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
                        RlpStreamDecoder.decode(reader);
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
                        RlpStreamDecoder.decode(reader);
                    }
                }
        );
    }


}
