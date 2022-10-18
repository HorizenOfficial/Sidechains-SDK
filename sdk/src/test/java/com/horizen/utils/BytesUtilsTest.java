package com.horizen.utils;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;
import com.horizen.params.MainNetParams;
import com.horizen.params.NetworkParams;
import com.horizen.params.TestNetParams;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class BytesUtilsTest {

    @Test
    public void BytesUtilsTest_getShort() {
        byte[] bytes = {0, 0, 1, 0};
        assertEquals("Values expected to by equal", 0, BytesUtils.getShort(bytes, 0));
        assertEquals("Values expected to by equal", 1, BytesUtils.getShort(bytes, 1));
        assertEquals("Values expected to by equal", 256, BytesUtils.getShort(bytes, 2));

        boolean exceptionOccurred = false;
        try {
            BytesUtils.getShort(bytes, 3);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);

        exceptionOccurred = false;
        try {
            BytesUtils.getShort(bytes, -1);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);
    }

    @Test
    public void BytesUtilsTest_getReversedShort() {
        byte[] bytes = {0, 0, 1, 0};
        assertEquals("Values expected to by equal", 0, BytesUtils.getReversedShort(bytes, 0));
        assertEquals("Values expected to by equal", 256, BytesUtils.getReversedShort(bytes, 1));
        assertEquals("Values expected to by equal", 1, BytesUtils.getReversedShort(bytes, 2));

        boolean exceptionOccurred = false;
        try {
            BytesUtils.getReversedShort(bytes, 3);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);

        exceptionOccurred = false;
        try {
            BytesUtils.getReversedShort(bytes, -1);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);
    }

    @Test
    public void BytesUtilsTest_getInt() {
        byte[] bytes = {0, 0, 0, 0, 1, 0, 0, 1};
        assertEquals("Values expected to by equal", 0, BytesUtils.getInt(bytes, 0));
        assertEquals("Values expected to by equal", 1, BytesUtils.getInt(bytes, 1));
        assertEquals("Values expected to by equal", 16777217, BytesUtils.getInt(bytes, 4));

        boolean exceptionOccurred = false;
        try {
            BytesUtils.getInt(bytes, 5);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);

        exceptionOccurred = false;
        try {
            BytesUtils.getInt(bytes, -1);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);
    }

    @Test
    public void BytesUtilsTest_getReversedInt() {
        byte[] bytes = {0, 0, 0, 0, 1, 0, 1, 0};
        assertEquals("Values expected to by equal", 0, BytesUtils.getReversedInt(bytes, 0));
        assertEquals("Values expected to by equal", 16777216, BytesUtils.getReversedInt(bytes, 1));
        assertEquals("Values expected to by equal", 65537, BytesUtils.getReversedInt(bytes, 4));

        boolean exceptionOccurred = false;
        try {
            BytesUtils.getReversedInt(bytes, 5);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);

        exceptionOccurred = false;
        try {
            BytesUtils.getReversedInt(bytes, -1);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);
    }

    @Test
    public void BytesUtilsTest_getLong() {
        byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1};
        assertEquals("Values expected to by equal", 0L, BytesUtils.getLong(bytes, 0));
        assertEquals("Values expected to by equal", 1L, BytesUtils.getLong(bytes, 1));
        assertEquals("Values expected to by equal", 72057594037927937L, BytesUtils.getLong(bytes, 8));

        boolean exceptionOccurred = false;
        try {
            BytesUtils.getLong(bytes, 9);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);

        exceptionOccurred = false;
        try {
            BytesUtils.getLong(bytes, -1);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);
    }

    @Test
    public void BytesUtilsTest_getReversedLong() {
        byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0};
        assertEquals("Values expected to by equal", 0L, BytesUtils.getReversedLong(bytes, 0));
        assertEquals("Values expected to by equal", 72057594037927936L, BytesUtils.getReversedLong(bytes, 1));
        assertEquals("Values expected to by equal", 281474976710657L, BytesUtils.getReversedLong(bytes, 8));

        boolean exceptionOccurred = false;
        try {
            BytesUtils.getReversedLong(bytes, 9);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);

        exceptionOccurred = false;
        try {
            BytesUtils.getReversedLong(bytes, -1);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);
    }

    @Test
    public void BytesUtilsTest_getCompactSize() {
        // Test 1: Try to parse CompactSize with size 1 - single byte
        for(long i = 0; i <= 252; i++) {
            CompactSize vi = BytesUtils.getCompactSize(new byte[]{ (byte)i }, 0);
            assertEquals("Value expected to have size 1", 1, vi.size());
            assertEquals("Values expected to by equal", i, vi.value());
        }

        // Test 2: Try to parse CompactSize with size 3 - short
        byte[] shortPrefix = new byte[]{(byte)253};
        for(long i = 253; i <= 65535; i = i<<1) {
            byte[] bv = Bytes.concat(shortPrefix, BytesUtils.reverseBytes(Shorts.toByteArray((short)i)));
            CompactSize vi = BytesUtils.getCompactSize(bv, 0);
            assertEquals("Value expected to have size 3", 3, vi.size());
            assertEquals("Values expected to by equal", i, vi.value());
        }

        // Test 3: Try to parse CompactSize with size 5 - int
        byte[] intPrefix = new byte[]{(byte)254};
        for(long i = 65536; i <= CompactSize.MAX_SERIALIZED_COMPACT_SIZE ; i = i<<1) {
            byte[] bv = Bytes.concat(intPrefix, BytesUtils.reverseBytes(Ints.toByteArray((int) i)));
            CompactSize vi = BytesUtils.getCompactSize(bv, 0);
            assertEquals("Value expected to have size 5", 5, vi.size());
            assertEquals("Values expected to by equal", i, vi.value());
        }

//        // Test 4: Try to parse CompactSize with size 9 - long
//        // Note: java long is "signed", but bitcoin use "unsigned long" type.
//        byte[] longPrefix = new byte[]{(byte)255};
//        for(long i = 4294967296L; i > 0; i = i<<1) {
//            byte[] bv = Bytes.concat(longPrefix,BytesUtils.reverseBytes(Longs.toByteArray(i)));
//            CompactSize vi = BytesUtils.getCompactSize(bv, 0);
//            assertEquals("Value expected to have size 9", 9, vi.size());
//            assertEquals("Values expected to by equal", i, vi.value());
//        }

        byte[] bytes = { 42 };
        // Test 5: out of bound offset
        boolean exceptionOccurred = false;
        try {
            BytesUtils.getCompactSize(bytes, bytes.length);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertTrue("Offset is out of bound exception expected", exceptionOccurred);

        exceptionOccurred = false;
        try {
            BytesUtils.getCompactSize(bytes, -1);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertTrue("Offset is out of bound exception expected", exceptionOccurred);
    }

    @Test
    public void BytesUtilsTest_reverseBytes() {
        byte[] bytes = {1, 2, 3, 4};
        byte[] expectedBytes = {4, 3, 2, 1};
        byte[] resBytes = BytesUtils.reverseBytes(bytes);
        assertEquals("Arrays expected to be equal", true, Arrays.equals(expectedBytes, resBytes));
    }

    @Test
    public void BytesUtilsTest_fromHexString() {
        byte[] bytes = Ints.toByteArray(47148);
        String expectedRes = "0000b82c";

        String res = BytesUtils.toHexString(bytes);
        assertEquals("Hex strings expected to be equal", expectedRes, res);
    }


    @Test
    public void BytesUtilsTest_toHexString() {
        String hex = "0000b82c";
        byte[] expectedRes = Ints.toByteArray(47148);

        assertEquals("Hex strings expected to be equal", true, Arrays.equals(expectedRes, BytesUtils.fromHexString(hex)));
    }


    @Test
    public void fromHorizenPublicKeyAddress() {
        // Test 1: valid MainNet addresses in MainNet network
        NetworkParams mainNetParams = new MainNetParams(null, null, null, null, null, 1, 0,100, 120, 720, null, null,0, null, null, null, null, null, null, null, false, null, null, true);
        String pubKeyAddressMainNet = "znc3p7CFNTsz1s6CceskrTxKevQLPoDK4cK";
        byte[] expectedPublicKeyHashBytesMainNet = BytesUtils.fromHexString("7843a3fcc6ab7d02d40946360c070b13cf7b9795");

        assertArrayEquals("Horizen base 58 check address expected to have different public key hash.",
                expectedPublicKeyHashBytesMainNet,
                BytesUtils.fromHorizenPublicKeyAddress(pubKeyAddressMainNet, mainNetParams));


        // Test 2: invalid MainNetAddress in MainNet network: broken checksum
        String invalidPubKeyAddressMainNet = "znc3p7CFNTsz1s6CceskrTxKevQLPoDK4c1";
        boolean exceptionOccurred = false;
        try {
            BytesUtils.fromHorizenPublicKeyAddress(invalidPubKeyAddressMainNet, mainNetParams);
        } catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertTrue("Invalid checksum Horizen base 58 check address expected to throw exception during parsing.", exceptionOccurred);


        // Test 3: invalid MainNetAddress in MainNet network: broken length
        String invalidLengthPubKeyAddressMainNet = "znc3p7CFNTsz1s6CceskrTxKevQLPoDK4c";
        exceptionOccurred = false;
        try {
            BytesUtils.fromHorizenPublicKeyAddress(invalidLengthPubKeyAddressMainNet, mainNetParams);
        } catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertTrue("Invalid length Horizen base 58 check address expected to throw exception during parsing.", exceptionOccurred);


        // Test 4: TestNetAddress in MainNet network
        String invalidNetworkPubKeyAddress = "ztkxeiFhYTS5sueyWSMDa8UiNr5so6aDdYi"; // from testnet
        exceptionOccurred = false;
        try {
            BytesUtils.fromHorizenPublicKeyAddress(invalidNetworkPubKeyAddress, mainNetParams);
        } catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertTrue("Invalid network Horizen base 58 check address expected to throw exception during parsing.", exceptionOccurred);


        // Test 5: valid TestNet addresses in TestNet network
        NetworkParams testNetParams = new TestNetParams(null, null, null, null, null, 1, 0,100, 120, 720,  null,null,0,  null,null, null, null, null, null, null, false, null, null, true);
        String pubKeyAddressTestNet = "ztkxeiFhYTS5sueyWSMDa8UiNr5so6aDdYi";
        byte[] expectedPublicKeyHashBytesTestNet = BytesUtils.fromHexString("c34e9f61c39bf4fa6225fcf715b59c195c12a6d7");
        assertArrayEquals("Horizen base 58 check address expected to have different public key hash.",
                expectedPublicKeyHashBytesTestNet,
                BytesUtils.fromHorizenPublicKeyAddress(pubKeyAddressTestNet, testNetParams));


        // Test 6: MainNetAddress in TestNet network
        invalidNetworkPubKeyAddress = "znc3p7CFNTsz1s6CceskrTxKevQLPoDK4cK"; // from testnet
        exceptionOccurred = false;
        try {
            BytesUtils.fromHorizenPublicKeyAddress(invalidNetworkPubKeyAddress, testNetParams);
        } catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertTrue("Invalid network Horizen base 58 check address expected to throw exception during parsing.", exceptionOccurred);
    }

    @Test
    public void toHorizenPublicKeyAddress() {
        // Test 1: valid MainNet addresses in MainNet network
        NetworkParams mainNetParams = new MainNetParams(null, null, null, null, null, 1, 0,100, 120, 720, null, null,0, null, null, null, null, null, null, null, false, null, null, true);
        byte[] publicKeyHashBytesMainNet = BytesUtils.fromHexString("7843a3fcc6ab7d02d40946360c070b13cf7b9795");
        String expectedPubKeyAddressMainNet = "znc3p7CFNTsz1s6CceskrTxKevQLPoDK4cK";

        assertEquals("Public key hash bytes expected to lead to different Horizen base 58 check address.",
                expectedPubKeyAddressMainNet,
                BytesUtils.toHorizenPublicKeyAddress(publicKeyHashBytesMainNet, mainNetParams));


        // Test 2: valid TestNet addresses in TestNet network
        NetworkParams testNetParams = new TestNetParams(null, null, null, null, null, 1, 0,100, 120, 720, null, null,0, null, null, null, null, null, null, null, false, null, null, true);
        byte[] publicKeyHashBytesTestNet = BytesUtils.fromHexString("c34e9f61c39bf4fa6225fcf715b59c195c12a6d7");
        String expectedPubKeyAddressTestNet = "ztkxeiFhYTS5sueyWSMDa8UiNr5so6aDdYi";

        assertEquals("Public key hash bytes expected to lead to different Horizen base 58 check address.",
                expectedPubKeyAddressTestNet,
                BytesUtils.toHorizenPublicKeyAddress(publicKeyHashBytesTestNet, testNetParams));


        // Test 3: invalid public key hash bytes length
        boolean exceptionOccurred = false;
        try {
            BytesUtils.toHorizenPublicKeyAddress(new byte[21], testNetParams);
        } catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertTrue("Invalid length Horizen public key hash expected to throw exception during parsing.", exceptionOccurred);
    }

    @Test
    public void getBytesFromBits() {
        assertEquals("Different byte size expected.", 0, BytesUtils.getBytesFromBits(-1));
        assertEquals("Different byte size expected.", 0, BytesUtils.getBytesFromBits(0));
        assertEquals("Different byte size expected.", 1, BytesUtils.getBytesFromBits(3));
        assertEquals("Different byte size expected.", 1, BytesUtils.getBytesFromBits(8));
        assertEquals("Different byte size expected.", 2, BytesUtils.getBytesFromBits(9));
    }
}