package com.horizen.account.utils;


import sparkz.crypto.hash.Blake2b256;

import java.security.*;
import java.util.Arrays;

class ChaChaPrngSecureRandomProvider extends Provider {
    public ChaChaPrngSecureRandomProvider() {
        super(ChaChaPrngSecureRandom.NAME,
                "1.0",
                "A Cryptographically-Secure PRNG based on ChaCha20");
        put("SecureRandom." + ChaChaPrngSecureRandom.NAME, ChaChaPrngSecureRandom.class.getName());
        put("SecureRandom." + ChaChaPrngSecureRandom.NAME + " ImplementedIn", "Software");
    }
}

public class ChaChaPrngSecureRandom extends SecureRandomSpi implements SecureRandomParameters {
    private int[] mState = new int[16];
    private final int[] mWorkingState = new int[16];
    private static final int ROUNDS = 20;
    private int mWordIndex;
    private long mStream = 0;
    private static final int DEFAULT_WORD_INDEX = 16;

    protected static final String NAME = "ChaChaPRNG";

    public static SecureRandom getInstance(byte[] seed) throws SecurityException {
        Provider[] providers = Security.getProviders("SecureRandom." + NAME);
        if ((providers == null)
            || (providers.length < 1)
            || (!providers[0].getClass().equals(ChaChaPrngSecureRandomProvider.class))){
            Security.insertProviderAt(new ChaChaPrngSecureRandomProvider(), 1);
        }

        SecureRandom rng;
        try {
            rng = SecureRandom.getInstance(NAME);
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException(NAME + " not available", e);
        }
        if (!ChaChaPrngSecureRandomProvider.class.equals(rng.getProvider().getClass())) {
            throw new SecurityException("SecureRandom.getInstance(\"" + NAME + "\") backed by wrong provider: " + rng.getProvider().getClass());
        }
        rng.setSeed(seed);
        return rng;
    }

    private void block() {
        int x0 = mState[0];
        int x1 = mState[1];
        int x2 = mState[2];
        int x3 = mState[3];
        int x4 = mState[4];
        int x5 = mState[5];
        int x6 = mState[6];
        int x7 = mState[7];
        int x8 = mState[8];
        int x9 = mState[9];
        int x10 = mState[10];
        int x11 = mState[11];
        int x12 = mState[12];
        int x13 = mState[13];
        int x14 = mState[14];
        int x15 = mState[15];

        for (int i = 0; i < ROUNDS; i += 2) {
            int[] tmp = quarterRound(x0, x4, x8, x12);
            x0 = tmp[0];
            x4 = tmp[1];
            x8 = tmp[2];
            x12 = tmp[3];

            tmp = quarterRound(x1, x5, x9, x13);
            x1 = tmp[0];
            x5 = tmp[1];
            x9 = tmp[2];
            x13 = tmp[3];

            tmp = quarterRound(x2, x6, x10, x14);
            x2 = tmp[0];
            x6 = tmp[1];
            x10 = tmp[2];
            x14 = tmp[3];

            tmp = quarterRound(x3, x7, x11, x15);
            x3 = tmp[0];
            x7 = tmp[1];
            x11 = tmp[2];
            x15 = tmp[3];

            tmp = quarterRound(x0, x5, x10, x15);
            x0 = tmp[0];
            x5 = tmp[1];
            x10 = tmp[2];
            x15 = tmp[3];

            tmp = quarterRound(x1, x6, x11, x12);
            x1 = tmp[0];
            x6 = tmp[1];
            x11 = tmp[2];
            x12 = tmp[3];

            tmp = quarterRound(x2, x7, x8, x13);
            x2 = tmp[0];
            x7 = tmp[1];
            x8 = tmp[2];
            x13 = tmp[3];

            tmp = quarterRound(x3, x4, x9, x14);
            x3 = tmp[0];
            x4 = tmp[1];
            x9 = tmp[2];
            x14 = tmp[3];
        }

        mWorkingState[0] = x0 + mState[0];
        mWorkingState[1] = x1 + mState[1];
        mWorkingState[2] = x2 + mState[2];
        mWorkingState[3] = x3 + mState[3];
        mWorkingState[4] = x4 + mState[4];
        mWorkingState[5] = x5 + mState[5];
        mWorkingState[6] = x6 + mState[6];
        mWorkingState[7] = x7 + mState[7];
        mWorkingState[8] = x8 + mState[8];
        mWorkingState[9] = x9 + mState[9];
        mWorkingState[10] = x10 + mState[10];
        mWorkingState[11] = x11 + mState[11];
        mWorkingState[12] = x12 + mState[12];
        mWorkingState[13] = x13 + mState[13];
        mWorkingState[14] = x14 + mState[14];
        mWorkingState[15] = x15 + mState[15];
    }

    private void incrementCounter() {
        mState[12]++;
        if (mState[12] == 0) {
            mState[13]++;
            if (mState[13] == 0) {
                // throw new Exception("chacha: counter overflow");
                mStream++;
                if (mStream == 0) {
                    repackState();
                } else {
                    mState[14] = (int)mStream;
                    mState[15] = (int)(mStream >> 32);
                }
            }
        }
    }

    private static int[] quarterRound(int a, int b, int c, int d) {
        a += b;
        d = rotateLeft32(d ^ a, 16);

        c += d;
        b = rotateLeft32(b ^ c, 12);

        a += b;
        d = rotateLeft32(d ^ a, 8);

        c += d;
        b = rotateLeft32(b ^ c, 7);
        return new int[]{a, b, c, d};
    }

    /**
     * @param x The value to be rotated
     * @param k The number of bits to rotate left
     */
    private static int rotateLeft32(int x, int k) {
        final int n = 32;

        int s = k & (n - 1);
        return x << s | x >>(n - s);
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
                (int)(stream >> 32),
        };
    }

    private void repackState() {
        byte[] seed = new byte[32];
        for (int i = 0, j = 0; i < 32; i += 4, j++) {
            seed[i] = (byte)(mState[j + 4] >> 24);
            seed[i + 1] = (byte)(mState[j + 4] >> 16);
            seed[i + 2] = (byte)(mState[j + 4] >> 8);
            seed[i + 3] = (byte)mState[j + 4];
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
        byte[] toHash = new byte[32 + seed.length];
        // Add a prefix for domain separation
        Arrays.fill(toHash, 0, 32, (byte)0xff);
        System.arraycopy(seed, 0, toHash, 32, seed.length);
        seed = Blake2b256.hash(toHash);
        for (int i = 0, j = 0; i < 32; i += 4, j++) {
            intSeed[j] = seed[i] << 24;
            intSeed[j] |= seed[i + 1] << 16;
            intSeed[j] |= seed[i + 2] << 8;
            intSeed[j] |= seed[i + 3];
        }
        mStream = 0;
        mState = defaultState(intSeed, mStream);
        mWordIndex = DEFAULT_WORD_INDEX;
    }

    @Override
    protected void engineNextBytes(byte[] bytes) {
        int count = bytes.length;
        int tail = count % 4;

        for (int i = 0; i < (count - tail); i += 4) {
            int word = getInt();
            bytes[i] = (byte) word;
            bytes[i + 1] = (byte)(word >> 8);
            bytes[i + 2] = (byte)(word >> 16);
            bytes[i + 3] = (byte)(word >> 24);
        }
        if (tail > 0) {
            int word = getInt();
            for (int i = tail; i > 0; i--) {
                bytes[count - i] = (byte)word;
                word >>= 8;
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
