package com.horizen.account.utils;

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
                -87,92,60,96,-37,2,63,-112,-80,94,-49,-33,-121,-128,59,-68,79,-84,-1,-1,-43,-61,-1,-1,68,-41,96,-47,51,113,-125,33        };
        byte[] expected2 = {
                75,-99,97,-83,117,117,95,46,125,39,90,11,18,61,93,-26,-94,-1,-1,-1,99,-24,94,-104,-25,-1,-1,-1,1,-127,98,52
        };
        byte[] output = new byte[32];
        SecureRandom rng = ChaChaPrngSecureRandom.getInstance(seed);
        rng.nextBytes(output);
        assertArrayEquals(expected1, output);
        rng.nextBytes(output);
        assertArrayEquals(expected2, output);
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
