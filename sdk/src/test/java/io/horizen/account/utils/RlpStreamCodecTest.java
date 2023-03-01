package io.horizen.account.utils;

import com.google.common.primitives.Bytes;
import io.horizen.utils.BytesUtils;
import org.junit.Test;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.utils.Numeric;
import sparkz.util.ByteArrayBuilder;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.VLQByteBufferReader;
import sparkz.util.serialization.VLQByteBufferWriter;
import sparkz.util.serialization.Writer;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class RlpStreamCodecTest {

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

        // encode using a stream
        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(rlpInt, writer);
        byte[] encodedBytesStream = writer.toBytes();

        // encode in the w3j way
        byte[] encodedBytes = RlpEncoder.encode(rlpInt);

        // check the encoding results are the same
        assertArrayEquals(encodedBytesStream, encodedBytes);

        // check we have the expected contents
        assertEquals(BytesUtils.toHexString(encodedBytes), "820400");

        // decode using a stream and check
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

        // encode using a stream
        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(rlpEncodedList, writer);
        byte[] encodedBytesStream = writer.toBytes();

        // encode in the w3j way
        byte[] encodedBytes = RlpEncoder.encode(rlpEncodedList);

        // check the encoding results are the same
        assertArrayEquals(encodedBytesStream, encodedBytes);

        // check we have the expected contents
        assertEquals(BytesUtils.toHexString(encodedBytes), "c88363617483646f67");

        // decode using a stream and check
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

        // encode using a stream
        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(rlpEncodedString, writer);
        byte[] encodedBytesStream = writer.toBytes();

        // encode in the w3j way
        byte[] encodedBytes = RlpEncoder.encode(rlpEncodedString);

        // check the encoding results are the same
        assertArrayEquals(encodedBytesStream, encodedBytes);

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
    public void rlpCodecSentenceWrong() {
        // similar sentence as above without the last char
        // not minimal encoding, when the string length is < 56 should be b74C not b8374c ...
        byte[] encodedBytes2 = BytesUtils.fromHexString(
                "b8374C6F72656D20697073756D20646F6C6F722073697420616D65742C20636F6E73656374657475722061646970697363696E6720656C6974");

        Reader r = new VLQByteBufferReader(ByteBuffer.wrap(encodedBytes2));
        assertThrows(
                "Exception expected, because data are a decoded invalid RLP list",
                RuntimeException.class,
                () -> RlpStreamDecoder.decode(r)
        );

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
    public void rlpStreamWritersTest() {

        VLQByteBufferWriter writer = new VLQByteBufferWriter(new ByteArrayBuilder());
        writer.putUByte(255);
        int wl1 = writer.length();
        assertEquals(wl1, 1);

        // create a new empty stream writer and write to it
        Writer newWriter = writer.newWriter();
        int wl2 = newWriter.length();
        assertEquals(wl2, 0);
        newWriter.putUByte(16);

        // get its contents and put them to original stream
        byte[] res1 = ((ByteArrayBuilder) newWriter.result()).toBytes();
        writer.putBytes(res1);

        // original stream writer has expected contents
        byte[] res2 = writer.result().toBytes();
        assertArrayEquals(res2, new byte[]{(byte)255, (byte)16});

        // append old stream to the new one and check contents are as expected
        newWriter.append(writer);
        byte[] res3 = ((ByteArrayBuilder) newWriter.result()).toBytes();
        assertArrayEquals(res3, new byte[]{(byte)16, (byte)255, (byte)16});
    }

    /*
     * Adapted from:
     * https://github.com/web3j/web3j/blob/master/rlp/src/test/java/org/web3j/rlp/RlpDecoderTest.java
     */
    @Test
    public void testRLPDecode() {

        // big positive number should stay positive after encoding-decoding
        // https://github.com/web3j/web3j/issues/562
        long value = 3000000000L;
        assertEquals(
                RlpString.create(BigInteger.valueOf(value)).asPositiveBigInteger().longValue(),
                (value));

        // empty array of binary
        Reader r = new VLQByteBufferReader(ByteBuffer.wrap(new byte[] {}));
        assertTrue(RlpStreamDecoder.decode(r).getValues().isEmpty());

        // The string "dog" = [ 0x83, 'd', 'o', 'g' ]
        r = new VLQByteBufferReader(ByteBuffer.wrap(new byte[] {(byte) 0x83, 'd', 'o', 'g'}));
        assertEquals(
                RlpStreamDecoder.decode(r).getValues().get(0),
                (RlpString.create("dog")));

        // The list [ "cat", "dog" ] = [ 0xc8, 0x83, 'c', 'a', 't', 0x83, 'd', 'o', 'g' ]
        r = new VLQByteBufferReader(ByteBuffer.wrap(
                new byte[] {
                        (byte) 0xc8,
                        (byte) 0x83,
                        'c',
                        'a',
                        't',
                        (byte) 0x83,
                        'd',
                        'o',
                        'g'
                }
        ));
        RlpList rlpList =
                (RlpList)
                        RlpStreamDecoder.decode(r)
                                .getValues()
                                .get(0);

        assertEquals(rlpList.getValues().get(0), (RlpString.create("cat")));

        assertEquals(rlpList.getValues().get(1), (RlpString.create("dog")));

        // The empty string ('null') = [ 0x80 ]
        r = new VLQByteBufferReader(ByteBuffer.wrap(new byte[] {(byte) 0x80}));
        assertEquals(
                RlpStreamDecoder.decode(r).getValues().get(0),
                (RlpString.create("")));

        r = new VLQByteBufferReader(ByteBuffer.wrap(new byte[] {(byte) 0x80}));
        assertEquals(
                RlpStreamDecoder.decode(r).getValues().get(0),
                (RlpString.create(new byte[] {})));

        r = new VLQByteBufferReader(ByteBuffer.wrap(new byte[] {(byte) 0x80}));
        assertEquals(
                RlpStreamDecoder.decode(r).getValues().get(0),
                (RlpString.create(BigInteger.ZERO)));

        // The empty list = [ 0xc0 ]
        r = new VLQByteBufferReader(ByteBuffer.wrap(new byte[] {(byte) 0xc0}));
        assertEquals(
                RlpStreamDecoder.decode(r).getValues().get(0).getClass(),
                (RlpList.class));
        r = new VLQByteBufferReader(ByteBuffer.wrap(new byte[] {(byte) 0xc0}));
        assertTrue(
                ((RlpList) RlpStreamDecoder.decode(r).getValues().get(0))
                        .getValues()
                        .isEmpty());

        // The encoded integer 0 ('\x00') = [ 0x00 ]
        r = new VLQByteBufferReader(ByteBuffer.wrap(new byte[] {(byte) 0x00}));
        assertEquals(
                RlpStreamDecoder.decode(r).getValues().get(0),
                (RlpString.create(BigInteger.valueOf(0).byteValue())));

        // The encoded integer 15 ('\x0f') = [ 0x0f ]
        r = new VLQByteBufferReader(ByteBuffer.wrap(new byte[] {(byte) 0x0f}));
        assertEquals(
                RlpStreamDecoder.decode(r).getValues().get(0),
                (RlpString.create(BigInteger.valueOf(15).byteValue())));

        // The encoded integer 1024 ('\x04\x00') = [ 0x82, 0x04, 0x00 ]
        r = new VLQByteBufferReader(ByteBuffer.wrap(new byte[] {(byte) 0x82, (byte) 0x04, (byte) 0x00}));
        assertEquals(
                RlpStreamDecoder.decode(r)
                        .getValues()
                        .get(0),
                (RlpString.create(BigInteger.valueOf(0x0400))));

        // The set theoretical representation of three,
        // [ [], [[]], [ [], [[]] ] ] = [ 0xc7, 0xc0, 0xc1, 0xc0, 0xc3, 0xc0, 0xc1, 0xc0 ]
        r = new VLQByteBufferReader(ByteBuffer.wrap(
                new byte[] {
                        (byte) 0xc7,
                        (byte) 0xc0,
                        (byte) 0xc1,
                        (byte) 0xc0,
                        (byte) 0xc3,
                        (byte) 0xc0,
                        (byte) 0xc1,
                        (byte) 0xc0
                }
        ));
        rlpList = RlpStreamDecoder.decode(r);
        assertEquals(rlpList.getClass(), RlpList.class);

        assertEquals(rlpList.getValues().size(), (1));

        assertEquals(rlpList.getValues().get(0).getClass(), (RlpList.class));

        assertEquals(((RlpList) rlpList.getValues().get(0)).getValues().size(), (3));

        assertEquals(
                ((RlpList) rlpList.getValues().get(0)).getValues().get(0).getClass(),
                RlpList.class);

        assertEquals(
                ((RlpList) ((RlpList) rlpList.getValues().get(0)).getValues().get(0))
                        .getValues()
                        .size(),
                (0));

        assertEquals(
                ((RlpList) ((RlpList) rlpList.getValues().get(0)).getValues().get(1))
                        .getValues()
                        .size(),
                (1));

        assertEquals(
                ((RlpList) ((RlpList) rlpList.getValues().get(0)).getValues().get(2))
                        .getValues()
                        .size(),
                (2));

        assertEquals(
                ((RlpList) ((RlpList) rlpList.getValues().get(0)).getValues().get(2))
                        .getValues()
                        .get(0)
                        .getClass(),
                (RlpList.class));

        assertEquals(
                ((RlpList)
                        ((RlpList)
                                ((RlpList) rlpList.getValues().get(0))
                                        .getValues()
                                        .get(2))
                                .getValues()
                                .get(0))
                        .getValues()
                        .size(),
                (0));

        assertEquals(
                ((RlpList)
                        ((RlpList)
                                ((RlpList) rlpList.getValues().get(0))
                                        .getValues()
                                        .get(2))
                                .getValues()
                                .get(1))
                        .getValues()
                        .size(),
                (1));

        // The string "Lorem ipsum dolor sit amet,
        // consectetur adipisicing elit" =
        // [ 0xb8, 0x38, 'L', 'o', 'r', 'e', 'm', ' ', ... , 'e', 'l', 'i', 't' ]
        r = new VLQByteBufferReader(ByteBuffer.wrap(
                new byte[] {
                        (byte) 0xb8,
                        (byte) 0x38,
                        'L',
                        'o',
                        'r',
                        'e',
                        'm',
                        ' ',
                        'i',
                        'p',
                        's',
                        'u',
                        'm',
                        ' ',
                        'd',
                        'o',
                        'l',
                        'o',
                        'r',
                        ' ',
                        's',
                        'i',
                        't',
                        ' ',
                        'a',
                        'm',
                        'e',
                        't',
                        ',',
                        ' ',
                        'c',
                        'o',
                        'n',
                        's',
                        'e',
                        'c',
                        't',
                        'e',
                        't',
                        'u',
                        'r',
                        ' ',
                        'a',
                        'd',
                        'i',
                        'p',
                        'i',
                        's',
                        'i',
                        'c',
                        'i',
                        'n',
                        'g',
                        ' ',
                        'e',
                        'l',
                        'i',
                        't'
                }
        ));
        assertEquals(
                RlpStreamDecoder.decode(r)
                        .getValues()
                        .get(0),
                (RlpString.create("Lorem ipsum dolor sit amet, consectetur adipisicing elit")));

        // https://github.com/paritytech/parity/blob/master/util/rlp/tests/tests.rs#L239
        r = new VLQByteBufferReader(ByteBuffer.wrap(new byte[] {(byte) 0x00}));
        assertEquals(
                RlpStreamDecoder.decode(r).getValues().get(0),
                (RlpString.create(new byte[] {0})));

        r = new VLQByteBufferReader(ByteBuffer.wrap(
                new byte[] {
                (byte) 0xc6,
                (byte) 0x82,
                (byte) 0x7a,
                (byte) 0x77,
                (byte) 0xc1,
                (byte) 0x04,
                (byte) 0x01
        }));

        rlpList = RlpStreamDecoder.decode(r);

        assertEquals(((RlpList) rlpList.getValues().get(0)).getValues().size(), (3));

        assertEquals(
                ((RlpList) rlpList.getValues().get(0)).getValues().get(0).getClass(),
                (RlpString.class));

        assertEquals(
                ((RlpList) rlpList.getValues().get(0)).getValues().get(1).getClass(),
                (RlpList.class));

        assertEquals(
                ((RlpList) rlpList.getValues().get(0)).getValues().get(2).getClass(),
                (RlpString.class));

        assertEquals(
                ((RlpList) rlpList.getValues().get(0)).getValues().get(0),
                (RlpString.create("zw")));

        assertEquals(
                ((RlpList) ((RlpList) rlpList.getValues().get(0)).getValues().get(1))
                        .getValues()
                        .get(0),
                (RlpString.create(4)));

        assertEquals(
                ((RlpList) rlpList.getValues().get(0)).getValues().get(2), (RlpString.create(1)));

        // payload more than 55 bytes
        String data =
                "F86E12F86B80881BC16D674EC8000094CD2A3D9F938E13CD947EC05ABC7FE734D"
                        + "F8DD8268609184E72A00064801BA0C52C114D4F5A3BA904A9B3036E5E118FE0DBB987"
                        + "FE3955DA20F2CD8F6C21AB9CA06BA4C2874299A55AD947DBC98A25EE895AABF6B625C"
                        + "26C435E84BFD70EDF2F69";

        byte[] payload = Numeric.hexStringToByteArray(data);
        r = new VLQByteBufferReader(ByteBuffer.wrap(payload));

        rlpList = RlpStreamDecoder.decode(r);

        assertEquals(((RlpList) rlpList.getValues().get(0)).getValues().size(), (2));

        assertEquals(
                ((RlpList) rlpList.getValues().get(0)).getValues().get(0).getClass(),
                (RlpString.class));

        assertEquals(
                ((RlpList) rlpList.getValues().get(0)).getValues().get(1).getClass(),
                (RlpList.class));

        assertEquals(
                ((RlpList) ((RlpList) rlpList.getValues().get(0)).getValues().get(1))
                        .getValues()
                        .size(),
                (9));

        // Regression test: this would previously throw OutOfMemoryError as it tried to allocate 2GB
        // for the non-existent data
        var r2 = new VLQByteBufferReader(ByteBuffer.wrap(new byte[] {
                (byte) 0xbb, (byte) 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff
        }));
        assertThrows(
                RuntimeException.class,
                (() ->
                        RlpStreamDecoder.decode(r2)));
    }


    /*
     * Adapted from:
     * https://github.com/web3j/web3j/blob/master/rlp/src/test/java/org/web3j/rlp/RlpEncoderTest.java
     */
    @Test
    public void testEncode() {
        VLQByteBufferWriter w = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(RlpString.create("dog"), w);
        assertArrayEquals(w.toBytes(),
                (new byte[] {(byte) 0x83, 'd', 'o', 'g'}));

        w = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(new RlpList(RlpString.create("cat"), RlpString.create("dog")), w);
        assertArrayEquals(w.toBytes(),
                (new byte[] {(byte) 0xc8, (byte) 0x83, 'c', 'a', 't', (byte) 0x83, 'd', 'o', 'g'}));

        w = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(RlpString.create(""), w);
        assertArrayEquals(w.toBytes(), (new byte[] {(byte) 0x80}));

        w = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(RlpString.create(new byte[] {}), w);
        assertArrayEquals(
                w.toBytes(), (new byte[] {(byte) 0x80}));

        w = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(new RlpList(), w);
        assertArrayEquals(w.toBytes(), (new byte[] {(byte) 0xc0}));

        w = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(RlpString.create(BigInteger.valueOf(0x0f)), w);
        assertArrayEquals(w.toBytes(), (new byte[] {(byte) 0x0f}));

        w = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(RlpString.create(BigInteger.valueOf(0x0400)), w);
        assertArrayEquals(w.toBytes(),
                (new byte[] {(byte) 0x82, (byte) 0x04, (byte) 0x00}));

        w = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(
                new RlpList(
                        new RlpList(),
                        new RlpList(new RlpList()),
                        new RlpList(new RlpList(), new RlpList(new RlpList()))), w);

        assertArrayEquals(w.toBytes(),
                (new byte[] {
                        (byte) 0xc7,
                        (byte) 0xc0,
                        (byte) 0xc1,
                        (byte) 0xc0,
                        (byte) 0xc3,
                        (byte) 0xc0,
                        (byte) 0xc1,
                        (byte) 0xc0
                }));

        w = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(
                RlpString.create(
                        "Lorem ipsum dolor sit amet, consectetur adipisicing elit"), w);

        assertArrayEquals(w.toBytes(),
                (new byte[] {
                        (byte) 0xb8,
                        (byte) 0x38,
                        'L',
                        'o',
                        'r',
                        'e',
                        'm',
                        ' ',
                        'i',
                        'p',
                        's',
                        'u',
                        'm',
                        ' ',
                        'd',
                        'o',
                        'l',
                        'o',
                        'r',
                        ' ',
                        's',
                        'i',
                        't',
                        ' ',
                        'a',
                        'm',
                        'e',
                        't',
                        ',',
                        ' ',
                        'c',
                        'o',
                        'n',
                        's',
                        'e',
                        'c',
                        't',
                        'e',
                        't',
                        'u',
                        'r',
                        ' ',
                        'a',
                        'd',
                        'i',
                        'p',
                        'i',
                        's',
                        'i',
                        'c',
                        'i',
                        'n',
                        'g',
                        ' ',
                        'e',
                        'l',
                        'i',
                        't'
                }));

        w = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(RlpString.create(BigInteger.ZERO), w);
        assertArrayEquals(
                w.toBytes(), (new byte[] {(byte) 0x80}));

        // https://github.com/paritytech/parity-common/blob/master/rlp/tests/tests.rs#L237
        w = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(RlpString.create(new byte[] {0}), w);
        assertArrayEquals(w.toBytes(), (new byte[] {(byte) 0x00}));

        w = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(
                new RlpList(
                        RlpString.create("zw"),
                        new RlpList(RlpString.create(4)),
                        RlpString.create(1)), w);

        assertArrayEquals(w.toBytes(),
                (new byte[] {
                        (byte) 0xc6,
                        (byte) 0x82,
                        (byte) 0x7a,
                        (byte) 0x77,
                        (byte) 0xc1,
                        (byte) 0x04,
                        (byte) 0x01
                }));

        // 55 bytes. See https://github.com/web3j/web3j/issues/519
        byte[] encodeMe = new byte[55];
        Arrays.fill(encodeMe, (byte) 0);
        byte[] expectedEncoding = new byte[56];
        expectedEncoding[0] = (byte) 0xb7;
        System.arraycopy(encodeMe, 0, expectedEncoding, 1, encodeMe.length);
        w = new VLQByteBufferWriter(new ByteArrayBuilder());
        RlpStreamEncoder.encode(RlpString.create(encodeMe), w);
        assertArrayEquals(w.toBytes(), (expectedEncoding));
    }
}
