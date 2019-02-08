package com.horizen.utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class ParseBytesUtilsTest {

    @Test
    public void ParseBytesUtilsTest_getInt() {
        byte[] bytes = {0, 0, 0, 0, 1, 0, 0, 1};
        assertEquals("Values expected to by equal", 0, ParseBytesUtils.getInt(bytes, 0));
        assertEquals("Values expected to by equal", 1, ParseBytesUtils.getInt(bytes, 1));
        assertEquals("Values expected to by equal", 16777217, ParseBytesUtils.getInt(bytes, 4));

        boolean exceptionOccurred = false;
        try {
            ParseBytesUtils.getInt(bytes, 5);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);

        exceptionOccurred = false;
        try {
            ParseBytesUtils.getInt(bytes, -1);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);
    }

    @Test
    public void ParseBytesUtilsTest_getLong() {
        byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1};
        assertEquals("Values expected to by equal", 0L, ParseBytesUtils.getLong(bytes, 0));
        assertEquals("Values expected to by equal", 1L, ParseBytesUtils.getLong(bytes, 1));
        assertEquals("Values expected to by equal", 72057594037927937L, ParseBytesUtils.getLong(bytes, 8));

        boolean exceptionOccurred = false;
        try {
            ParseBytesUtils.getLong(bytes, 9);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);

        exceptionOccurred = false;
        try {
            ParseBytesUtils.getLong(bytes, -1);
        } catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Offset is out of bound exception expected", true, exceptionOccurred);
    }
}