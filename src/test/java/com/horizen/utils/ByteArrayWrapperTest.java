package com.horizen.utils;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class ByteArrayWrapperTest {

    @Test
    public void ByteArrayWrapperTest_GeneralTest() {
        byte[] b1 = {1, 2, 3, 4, 5};
        byte[] b2 = {1, 2, 3, 4, 5};
        byte[] b3 = {1, 2, 3, 2, 1};

        ByteArrayWrapper bw1 = new ByteArrayWrapper(b1);
        assertEquals("Data bytes expected to be the same", true, Arrays.equals(b1, bw1.data()));

        ByteArrayWrapper bw2 = new ByteArrayWrapper(b2);
        assertEquals("ByteArrayWrapper has codes expected to be equal", bw1.hashCode(), bw2.hashCode());
        assertEquals("ByteArrayWrapper expected to be equal", bw1, bw2);


        ByteArrayWrapper bw3 = new ByteArrayWrapper(b3);
        assertNotEquals("ByteArrayWrapper has codes expected to be different", bw1.hashCode(), bw3.hashCode());
        assertNotEquals("ByteArrayWrapper expected to be different", bw1, bw3);


        b1[1] = 6;
        assertEquals("Data bytes expected to be the same", true, Arrays.equals(b1, bw1.data()));
        assertNotEquals("Data bytes and source bytes expected to be different objects", b1, bw1.data());
    }

    @Test
    public void ByteArrayWrapperTest_ComparableTest() {
        assertEquals("Test is not implemented yet.", true, false);
    }
}