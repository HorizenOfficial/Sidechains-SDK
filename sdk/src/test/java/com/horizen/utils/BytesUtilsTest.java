package com.horizen.utils;

import com.google.common.primitives.Ints;
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
    public void BytesUtilsTest_getVarInt() {
        byte[] bytes = {4, (byte)253, 1, 2, (byte)254, 1, 0, 0, 2, (byte)255, 1, 0, 0, 0, 0, 1, 0, 1};

        // Test 1: Try to parse VarInt with size 1 - single byte
        VarInt vi = BytesUtils.getVarInt(bytes, 0);
        assertEquals("Value expected to have size 1", 1, vi.size());
        assertEquals("Values expected to by equal", 4L, vi.value());

        // Test 2: Try to parse VarInt with size 3 - short
        vi = BytesUtils.getVarInt(bytes, 1);
        assertEquals("Value expected to have size 3", 3, vi.size());
        assertEquals("Values expected to by equal", 258L, vi.value());

        // Test 3: Try to parse VarInt with size 5 - int
        vi = BytesUtils.getVarInt(bytes, 4);
        assertEquals("Value expected to have size 5", 5, vi.size());
        assertEquals("Values expected to by equal", 16777218L, vi.value());

        // Test 4: Try to parse VarInt with size 9 - long
        vi = BytesUtils.getVarInt(bytes, 9);
        assertEquals("Value expected to have size 9", 9, vi.size());
        assertEquals("Values expected to by equal", 72057594037993473L, vi.value());


        // Test 5: out of bound offset
        boolean exceptionOccurred = false;
        try {
            BytesUtils.getVarInt(bytes, 20);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);

        exceptionOccurred = false;
        try {
            BytesUtils.getVarInt(bytes, -1);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);
    }

    @Test
    public void BytesUtilsTest_getReversedVarInt() {
        byte[] bytes = {4, (byte)253, 1, 2, (byte)254, 1, 0, 0, 2, (byte)255, 1, 0, 0, 0, 0, 1, 0, 1};

        // Test 1: Try to parse Reversed VarInt with size 1 - single byte
        VarInt vi = BytesUtils.getReversedVarInt(bytes, 0);
        assertEquals("Value expected to have size 1", 1, vi.size());
        assertEquals("Values expected to by equal", 4L, vi.value());

        // Test 2: Try to parse VarInt with size 3 - short
        vi = BytesUtils.getReversedVarInt(bytes, 1);
        assertEquals("Value expected to have size 3", 3, vi.size());
        assertEquals("Values expected to by equal", 513L, vi.value());

        // Test 3: Try to parse VarInt with size 5 - int
        vi = BytesUtils.getReversedVarInt(bytes, 4);
        assertEquals("Value expected to have size 5", 5, vi.size());
        assertEquals("Values expected to by equal", 33554433L, vi.value());

        // Test 4: Try to parse VarInt with size 9 - long
        vi = BytesUtils.getReversedVarInt(bytes, 9);
        assertEquals("Value expected to have size 9", 9, vi.size());
        assertEquals("Values expected to by equal", 72058693549555713L, vi.value());


        // Test 5: out of bound offset
        boolean exceptionOccurred = false;
        try {
            BytesUtils.getReversedVarInt(bytes, 20);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);

        exceptionOccurred = false;
        try {
            BytesUtils.getReversedVarInt(bytes, -1);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);
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
}