package io.horizen.utils;

import org.junit.Test;
import java.security.SecureRandom;
import static org.junit.Assert.*;

public class ChaChaPrngSecureRandomTest {
    static int littleEndianToInt(byte[] data) {
        return (data[0] & 0xff) | ((data[1] & 0xff) << 8) | ((data[2] & 0xff) << 16) | ((data[3] & 0xff) << 24);
    }

    static double log2(double v) {
        return Math.log(v) / Math.log(2);
    }

    static boolean shannon(byte[] data) {
        // See Donald Knuth "A mathematical theory of communication"
        // p. 379-423
        final double THRESHOLD = 7.9;
        int[] buckets = new int[256];
        for (byte b : data) {
            int i = b & 0xff;
            buckets[i]++;
        }
        double length = data.length;
        double entropy = 0;
        for (int i : buckets) {
            double probability = ((double)i) / length;
            if (probability > 0.0) {
                entropy += probability * log2((1.0/probability));
            }
        }
        return entropy >= THRESHOLD;
    }

    static boolean mean(byte[] data) {
        // See Donald Knuth "A mathematical theory of communication"
        // p. 379-423
        final double THRESHOLD = 126;
        int[] buckets = new int[256];
        for (byte b : data) {
            int i = b & 0xff;
            buckets[i]++;
        }
        double length = data.length;
        double sum = 0;
        for (int i = 0; i < buckets.length; i++) {
            sum += ((double)i) * ((double)buckets[i]);
        }
        sum /= length;
        return sum >= THRESHOLD;
    }

    // See <https://www.geeksforgeeks.org/estimating-value-pi-using-monte-carlo>
    static boolean monteCarlo(byte[] data) {
        // (256**3-1)**2
        final double IN_CIRCLE_DISTANCE = 281474943156225.0;
        int[] monte = new int[6];
        int accumulator = 0;
        int tries = 0;
        int inCount = 0;

        for (byte b : data) {
            monte[accumulator] = b & 0xff;
            accumulator++;

            if (accumulator == monte.length) {
                accumulator = 0;
                tries++;

                double x = 0.0;
                double y = 0.0;
                for (int j = 0; j < monte.length / 2; j++) {
                    x = x * 256.0 + ((double)monte[j]);
                    y = y * 256.0 + ((double)monte[j + 3]);
                }

                if ((x * x + y * y) < IN_CIRCLE_DISTANCE) {
                    inCount++;
                }
            }
        }
        double result = 4.0 * (((double)inCount) / ((double)tries));
        return 3.1 <= result && result <= 3.2;
    }

    // Computes the serial correlation coefficient. In the event of input all 0s, reports 1.0
    // See Knuth, D. E. (1969). The art of computer programming, volume 2 / seminumerical
    // algorithms. Addison-Wesley
    static boolean serialCorrelationCoefficient(byte[] data) {
        // term 1
        double t1 = 0.0;
        // term 2
        double t2 = 0.0;
        // term 3
        double t3 = 0.0;
        // last byte
        double last = 0.0;
        // first byte
        double u0 = data[0] & 0xff;
        // total bytes processed
        double total = data.length;
        // Data index
        int i = 1;

        for (; i < data.length; i++) {
            double un = data[i] & 0xff;
            t1 += last * un;
            t2 += un;
            t3 += un * un;
            last = un;
        }
        t1 = t1 + last * u0;
        t2 = t2 * t2;
        double scc = total * t3 - t2;

        if (scc == 0.0) {
            // Should never see scc == 0.0 for non-zero input
            // Declare as positively correlated
            scc = 1.0;
        } else {
            scc = (total * t1 - t2) / scc;
        }

        return -0.004 <= scc && scc <= 0.004;
    }

    @Test
    public void entropyTests() {
        byte[] seed = {
                0, 0, 0, 0, 0, 0, 0, 0,
        };
        SecureRandom rng = ChaChaPrngSecureRandom.getInstance(seed);
        byte[] data = new byte[524288];
        rng.nextBytes(data);
        assertTrue(shannon(data));
        assertTrue(mean(data));
        assertTrue(monteCarlo(data));
        assertTrue(serialCorrelationCoefficient(data));
    }

    @Test
    public void simple32() {
        byte[] seed = {
                0, 0, 0, 0, 0, 0, 0, 0,
                1, 0, 0, 0, 0, 0, 0, 0,
                2, 0, 0, 0, 0, 0, 0, 0,
                3, 0, 0, 0, 0, 0, 0, 0,
        };
        SecureRandom rng = ChaChaPrngSecureRandom.getInstance(seed);
        byte[] bytes = new byte[4];
        rng.nextBytes(bytes);
        assertEquals(littleEndianToInt(bytes), 137206642);
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
        byte[] bytes = new byte[4];
        for (int e : expected) {
            rng.nextBytes(bytes);
            assertEquals(e, littleEndianToInt(bytes));
        }
        expected = new int[]{
            0xbee7079f, 0x7a385155, 0x7c97ba98, 0x0d082d73,
            0xa0290fcb, 0x6965e348, 0x3e53c612, 0xed7aee32,
            0x7621b729, 0x434ee69c, 0xb03371d5, 0xd539d874,
            0x281fed31, 0x45fb0a51, 0x1f0ae1ac, 0x6f4d794b,
        };
        for (int e : expected) {
            rng.nextBytes(bytes);
            assertEquals(e, littleEndianToInt(bytes));
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
        byte[] bytes = new byte[4];
        for (int e : expected) {
            rng.nextBytes(bytes);
            assertEquals(e, littleEndianToInt(bytes));
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
        // Test block 2 by skipping blocks 0 and 1
        SecureRandom rng = ChaChaPrngSecureRandom.getInstance(seed);
        for (int i = 0; i < 32; i++) {
            rng.nextInt();
        }
        byte[] bytes = new byte[4];
        for (int e : expected) {
            rng.nextBytes(bytes);
            assertEquals(e, littleEndianToInt(bytes));
        }
    }

    @Test
    public void testMultipleBlocks() {
        byte[] seed = {
            0, 0, 0, 0, 1, 0, 0, 0,
            2, 0, 0, 0, 3, 0, 0, 0,
            4, 0, 0, 0, 5, 0, 0, 0,
            6, 0, 0, 0, 7, 0, 0, 0,
        };
        int[] expected = {
            0xf225c81a, 0x6ab1be57, 0x04d42951, 0x70858036,
            0x49884684, 0x64efec72, 0x4be2d186, 0x3615b384,
            0x11cfa18e, 0xd3c50049, 0x75c775f6, 0x434c6530,
            0x2c5bad8f, 0x898881dc, 0x5f1c86d9, 0xc1f8e7f4,
        };
        SecureRandom rng = ChaChaPrngSecureRandom.getInstance(seed);
        byte[] bytes = new byte[4];
        // Test every 17th word
        for (int i = 0; i < 16; i++) {
            rng.nextBytes(bytes);
            assertEquals(expected[i], littleEndianToInt(bytes));
            for (int j = 0; j < 16; j++) {
                rng.nextInt();
            }
        }
    }
}
