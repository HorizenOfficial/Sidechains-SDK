package com.horizen.utils;

import com.google.common.primitives.Ints;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class BytesUtilsTest {

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