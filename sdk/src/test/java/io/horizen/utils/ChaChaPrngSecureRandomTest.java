package io.horizen.utils;

import org.junit.Test;
import java.security.SecureRandom;
import static org.junit.Assert.*;

public class ChaChaPrngSecureRandomTest {
//    @Test
//    public void outputExpected() throws Exception {
//        byte[] seed = {
//               3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3
//        };
//        byte[] expected1 = {
//                -23, 10, 77, -86, 22, 44, -100, 103, -85, -32, 33, -109, -98, -21, -48, -50, -74, -1, -1, -1, -85, -1, -1, -1, 99, 81, 81, -121, -26, 60, -91, -8
//        };
//        byte[] expected2 = {
//                7,-78,14,-84,-100,53,35,-110,85,10,79,49,-78,-21,-38,28,96,99,-59,16,-26,-1,-1,-1,-100,-1,-1,-1,-73,-1,-1,-1
//        };
//        byte[] output = new byte[32];
//        SecureRandom rng = ChaChaPrngSecureRandom.getInstance(seed);
//        rng.nextBytes(output);
//        if (output[0] != expected1[0]) {
//            throw new Exception(printArray(output));
//        }
//        assertArrayEquals(expected1, output);
//        rng.nextBytes(output);
//        if (output[0] != expected2[0]) {
//            throw new Exception(printArray(output));
//        }
//        assertArrayEquals(expected2, output);
//    }
//
//    @Test
//    public void ensureStreamVaries() {
//        byte[] seed = {
//                3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3
//        };
//        byte[] output = new byte[32];
//        byte[] output2 = new byte[32];
//        SecureRandom rng = ChaChaPrngSecureRandom.getInstance(seed);
//        for (int i = 0; i < 10; i++) {
//            rng.nextBytes(output);
//            rng.nextBytes(output2);
//            int equal = 0;
//            for (int j = 0; j < output.length; j++) {
//                equal |= output[j] ^ output2[j];
//            }
//            assertNotEquals(0, equal);
//        }
//    }

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

    @Test
    public void simple32() throws Exception {
        byte[] seed = {
                0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0,
                0, 0, 0,
        };
        SecureRandom rng = ChaChaPrngSecureRandom.getInstance(seed);
        int t = rng.nextInt();
        assertEquals(t, 137206642);
    }

    @Test
    public void trueValuesA() {
        // Test vectors 1 and 2 from
        // https://tools.ietf.org/html/draft-nir-cfrg-chacha20-poly1305-04
        byte[] seed = {
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
        };
        SecureRandom rng = ChaChaPrngSecureRandom.getInstance(seed);
        int[] expected = {
            0xade0b876, 0x903df1a0, 0xe56a5d40, 0x28bd8653,
            0xb819d2bd, 0x1aed8da0, 0xccef36a8, 0xc70d778b,
            0x7c5941da, 0x8d485751, 0x3fe02477, 0x374ad8b8,
            0xf4b8436a, 0x1ca11815, 0x69b687c3, 0x8665eeb2,
        };
        for (int e : expected) {
            assertEquals(e, rng.nextInt());
        }
        expected = new int[]{
            0xbee7079f, 0x7a385155, 0x7c97ba98, 0x0d082d73,
            0xa0290fcb, 0x6965e348, 0x3e53c612, 0xed7aee32,
            0x7621b729, 0x434ee69c, 0xb03371d5, 0xd539d874,
            0x281fed31, 0x45fb0a51, 0x1f0ae1ac, 0x6f4d794b,
        };
        for (int e : expected) {
            assertEquals(e, rng.nextInt());
        }
    }

    @Test
    public void trueValuesB() {
        // Test vector 3 from
        // https://tools.ietf.org/html/draft-nir-cfrg-chacha20-poly1305-04
        byte[] seed = {
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 1,
        };

        SecureRandom rng = ChaChaPrngSecureRandom.getInstance(seed);
        // Skip block 0
        for (int i = 0; i < 16; i++) {
            rng.nextInt();
        }

        int[] expected = {
            0x2452eb3a, 0x9249f8ec, 0x8d829d9b, 0xddd4ceb1,
            0xe8252083, 0x60818b01, 0xf38422b8, 0x5aaa49c9,
            0xbb00ca8e, 0xda3ba7b4, 0xc4b592d1, 0xfdf2732f,
            0x4436274e, 0x2561b3c8, 0xebdd4aa6, 0xa0136c00,
        };
        for (int e : expected) {
            assertEquals(e, rng.nextInt());
        }
    }

    @Test
    public void trueValuesC() {
        // Test vector 4 from
        // https://tools.ietf.org/html/draft-nir-cfrg-chacha20-poly1305-04
        byte[] seed = {
            0, (byte)0xff, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
        };
        int[] expected = {
            0xfb4dd572, 0x4bc42ef1, 0xdf922636, 0x327f1394,
            0xa78dea8f, 0x5e269039, 0xa1bebbc1, 0xcaf09aae,
            0xa25ab213, 0x48a6b46c, 0x1b9d9bcb, 0x092c5be6,
            0x546ca624, 0x1bec45d5, 0x87f47473, 0x96f0992e,
        };
        // Test block 2 by skipping block 0 and 1
        SecureRandom rng = ChaChaPrngSecureRandom.getInstance(seed);
        for (int i = 0; i < 32; i++) {
            rng.nextInt();
        }
        for (int e : expected) {
            assertEquals(e, rng.nextInt());
        }
    }
}
