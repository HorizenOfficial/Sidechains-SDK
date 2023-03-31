package io.horizen.utils;

import org.jetbrains.annotations.NotNull;
import sparkz.crypto.hash.Blake2b256;
import java.security.*;

public class ChaChaPrngSecureRandom extends SecureRandomSpi implements SecureRandomParameters {
    private int[] mState = new int[16];
    private final int[] mWorkingState = new int[16];
    private static final int ROUNDS = 20;
    private int mWordIndex;
    private long mStream = 0;
    private static final int DEFAULT_WORD_INDEX = 16;

    protected static final String ALGO = "ChaCha20PRNG";

    static {
        ChaChaPrngSecureRandomProvider.init();
    }

    @NotNull
    public static SecureRandom getInstance(byte[] seed) throws SecurityException {
        SecureRandom rng;
        try {
            rng = SecureRandom.getInstance(ALGO, ChaChaPrngSecureRandomProvider.NAME);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new SecurityException(ALGO + "/" + ChaChaPrngSecureRandomProvider.NAME + " not available", e);
        }
        if (!ChaChaPrngSecureRandomProvider.class.equals(rng.getProvider().getClass())) {
            throw new SecurityException("SecureRandom.getInstance(\"" + ALGO + "\", \"" + ChaChaPrngSecureRandomProvider.NAME + "\") backed by wrong provider: " + rng.getProvider().getClass());
        }
        rng.setSeed(seed);
        return rng;
    }

    private void block() {
        int[] x = new int[16];
        System.arraycopy(mState, 0, x, 0, 16);

        for (int i = 0; i < ROUNDS; i += 2) {
            quarterRound(x, 0, 4, 8, 12);
            quarterRound(x, 1, 5, 9, 13);
            quarterRound(x, 2, 6, 10, 14);
            quarterRound(x, 3, 7, 11, 15);
            quarterRound(x, 0, 5, 10, 15);
            quarterRound(x, 1, 6, 11, 12);
            quarterRound(x, 2, 7, 8, 13);
            quarterRound(x, 3, 4, 9, 14);
        }

        for (int i = 0; i < 16; i++) mWorkingState[i] = x[i] + mState[i];
    }

    private void incrementCounter() {
        mState[12]++;
        if (mState[12] == 0) {
            mState[13]++;
            if (mState[13] == 0) {
                mStream++;
                if (mStream == 0) {
                    repackState();
                } else {
                    mState[14] = (int)mStream;
                    mState[15] = (int)(mStream >>> 32);
                }
            }
        }
    }

    private static void quarterRound(@NotNull int[] x, int a, int b, int c, int d) {
        x[a] += x[b];
        x[d] = rotateLeft32(x[d] ^ x[a], 16);

        x[c] += x[d];
        x[b] = rotateLeft32(x[b] ^ x[c], 12);

        x[a] += x[b];
        x[d] = rotateLeft32(x[d] ^ x[a], 8);

        x[c] += x[d];
        x[b] = rotateLeft32(x[b] ^ x[c], 7);
    }

    /**
     * @param x The value to be rotated
     * @param k The number of bits to rotate left
     */
    private static int rotateLeft32(int x, int k) {
        return (x << k) | (x >>> (32 - k));
    }

    private static int[] defaultState(int[] seed, long stream) {
        return new int[]{
                0x61707865,
                0x3320646e,
                0x79622d32,
                0x6b206574,
                seed[0],
                seed[1],
                seed[2],
                seed[3],
                seed[4],
                seed[5],
                seed[6],
                seed[7],
                0,
                0,
                (int)stream,
                (int)(stream >>> 32),
        };
    }

    private void repackState() {
        byte[] seed = new byte[32];
        for (int i = 0, j = 0; i < 32; i += 4, j++) {
            seed[i] = (byte)mState[j + 4];
            seed[i + 1] = (byte)(mState[j + 4] >> 8);
            seed[i + 2] = (byte)(mState[j + 4] >> 16);
            seed[i + 3] = (byte)(mState[j + 4] >>> 24);
        }
        seed = Blake2b256.hash(seed);
        engineSetSeed(seed);
    }

    private int getInt() {
        if (mWordIndex == 16) {
            block();
            incrementCounter();
            mWordIndex = 0;
        }
        int result = mWorkingState[mWordIndex];
        mWordIndex++;
        return result;
    }

    @Override
    protected void engineSetSeed(byte[] seed) {
        int[] intSeed = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
        for (int i = 0, j = 0; i < 32; i += 4, j++) {
            intSeed[j] = seed[i];
            intSeed[j] |= seed[i + 1] << 8;
            intSeed[j] |= seed[i + 2] << 16;
            intSeed[j] |= seed[i + 3] << 24;
        }
        mStream = 0;
        mState = defaultState(intSeed, mStream);
        mWordIndex = DEFAULT_WORD_INDEX;
    }

    @Override
    protected void engineNextBytes(byte[] bytes) {
        int count = bytes.length;
        int tail = count % 4;

        // NOTE: Java expects big endian format to be used
        // But all test cases are little endian format
        for (int i = 0; i < (count - tail); i += 4) {
            int word = getInt();
            bytes[i] = (byte) word;
            bytes[i + 1] = (byte)(word >>> 8);
            bytes[i + 2] = (byte)(word >>> 16);
            bytes[i + 3] = (byte)(word >>> 24);
        }
        if (tail > 0) {
            int word = getInt();
            for (int i = tail; i > 0; i--) {
                bytes[count - i] = (byte)word;
                word >>>= 8;
            }
        }
    }

    @Override
    protected byte[] engineGenerateSeed(int numBytes) {
        byte[] seed = new byte[numBytes];
        engineNextBytes(seed);
        return seed;
    }
}
