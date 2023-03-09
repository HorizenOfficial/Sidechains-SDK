package io.horizen.utils;

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
        //assertNotEquals("Data bytes and source bytes expected to be different objects", b1, bw1.data());
    }

    @Test
    public void ByteArrayWrapperTest_ComparableTest() {

        // Compare two empty wrappers
        assertEquals("ByteArrayWrappers expected to be equal", true, new ByteArrayWrapper(new byte[0]).compareTo(new ByteArrayWrapper(new byte[0])) == 0);

        // Compare different combinations
        ByteArrayWrapper bw1 = new ByteArrayWrapper(BytesUtils.fromHexString("ba11"));
        ByteArrayWrapper bw2 = new ByteArrayWrapper(BytesUtils.fromHexString("ba11"));
        ByteArrayWrapper bw3 = new ByteArrayWrapper(BytesUtils.fromHexString("1221"));
        ByteArrayWrapper bw4 = new ByteArrayWrapper(BytesUtils.fromHexString("bc11"));
        ByteArrayWrapper bw5 = new ByteArrayWrapper(BytesUtils.fromHexString("ba11ae"));

        assertEquals("ByteArrayWrappers expected to be equal", true, bw1.compareTo(bw2) == 0);
        assertEquals("First ByteArrayWrapper expected to be greater than second one", true, bw1.compareTo(bw3) > 0);
        assertEquals("First ByteArrayWrapper expected to be less than second one", true, bw1.compareTo(bw4) < 0);
        assertEquals("First ByteArrayWrapper expected to be less than second one", true, bw1.compareTo(bw5) < 0);
    }
}
