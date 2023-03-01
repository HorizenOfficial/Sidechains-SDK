package io.horizen.utils;

import org.junit.Test;
import java.security.SecureRandom;
import static org.junit.Assert.*;

public class ChaChaPrngSecureRandomTest {
    @Test
    public void outputExpected() throws Exception {
        byte[] seed = {
               3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3
        };
        byte[] expected1 = {
                -23, 10, 77, -86, 22, 44, -100, 103, -85, -32, 33, -109, -98, -21, -48, -50, -74, -1, -1, -1, -85, -1, -1, -1, 99, 81, 81, -121, -26, 60, -91, -8
        };
        byte[] expected2 = {
                7,-78,14,-84,-100,53,35,-110,85,10,79,49,-78,-21,-38,28,96,99,-59,16,-26,-1,-1,-1,-100,-1,-1,-1,-73,-1,-1,-1
        };
        byte[] output = new byte[32];
        SecureRandom rng = ChaChaPrngSecureRandom.getInstance(seed);
        rng.nextBytes(output);
        if (output[0] != expected1[0]) {
            throw new Exception(printArray(output));
        }
        assertArrayEquals(expected1, output);
        rng.nextBytes(output);
        if (output[0] != expected2[0]) {
            throw new Exception(printArray(output));
        }
        assertArrayEquals(expected2, output);
    }

    @Test
    public void ensureStreamVaries() {
        byte[] seed = {
                3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3
        };
        byte[] output = new byte[32];
        byte[] output2 = new byte[32];
        SecureRandom rng = ChaChaPrngSecureRandom.getInstance(seed);
        for (int i = 0; i < 10; i++) {
            rng.nextBytes(output);
            rng.nextBytes(output2);
            int equal = 0;
            for (int j = 0; j < output.length; j++) {
                equal |= output[j] ^ output2[j];
            }
            assertNotEquals(0, equal);
        }
    }

    static String printArray(byte[] array) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        String sep = "";
        for (byte b : array) {
            builder.append(sep);
            builder.append(b);
            sep = ",";
        }
        builder.append("]");
        return builder.toString();
    }
}
