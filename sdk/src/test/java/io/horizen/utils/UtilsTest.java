package io.horizen.utils;

import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.*;

public class UtilsTest {

    @Test
    public void UtilsTest_doubleSHA256Hash() {
        // bin 0101010000010100000101000001010100000101000101000100000100010101
        byte[] bytes = {01, 01, 01, 00, 00, 01, 01, 00, 00, 01, 01, 00, 00, 01, 01, 01, 00, 00, 01, 01, 00, 01, 01, 00, 01, 00, 00, 01, 00, 01, 01, 01};
        // double sha-256 of bytes
        String expectedRes = "b7a065fdf1046168c2d1a0bcb36ef22b99c483bf6964f9be226ad4a723d933fc";
        byte[] res = Utils.doubleSHA256Hash(bytes);

        assertEquals("Hash length expected to be 32", Utils.SHA256_LENGTH, res.length);
        assertEquals("Different hash value expected", expectedRes, BytesUtils.toHexString(res));
    }

    @Test
    public void UtilsTest_doubleSHA256HashOfConcatenation() {
        // bin 0101010000010100000101000001010100000101000101000100000100010101
        byte[] bytes1 = {01, 01, 01, 00, 00, 01, 01, 00, 00, 01, 01, 00, 00, 01, 01, 01, 00, 00, 01, 01, 00, 01, 01, 00, 01, 00, 00, 01, 00, 01, 01, 01};

        // bin 0001010000010100000101000001010100000101000101000100000100010101
        byte[] bytes2 = {00, 01, 01, 00, 00, 01, 01, 00, 00, 01, 01, 00, 00, 01, 01, 01, 00, 00, 01, 01, 00, 01, 01, 00, 01, 00, 00, 01, 00, 01, 01, 01};

        // double sha-256 of bytes1+bytes2
        String expectedRes = "82c7cc49eda25fa7203d24cbec9c2319fa3a3fea972debe908bb80af2c430230";
        byte[] res = Utils.doubleSHA256HashOfConcatenation(bytes1, bytes2);

        assertEquals("Hash length expected to be 32", Utils.SHA256_LENGTH, res.length);
        assertEquals("Different hash value expected", expectedRes, BytesUtils.toHexString(res));
    }

    @Test
    public void UtilsTest_compactEncoding() throws Exception {
        assertEquals(new BigInteger("1234560000", 16), Utils.decodeCompactBits(0x05123456L));
        assertEquals(new BigInteger("c0de000000", 16), Utils.decodeCompactBits(0x0600c0de));
        assertEquals(0x05123456L, Utils.encodeCompactBits(new BigInteger("1234560000", 16)));
        assertEquals(0x0600c0deL, Utils.encodeCompactBits(new BigInteger("c0de000000", 16)));
    }
}