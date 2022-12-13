package com.horizen.cryptolibprovider.utils;

import com.horizen.librustsidechains.FieldElement;
import com.horizen.librustsidechains.Constants;
import com.horizen.utils.BytesUtils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class FieldElementUtils {
    public static int fieldElementLength() {
        return Constants.FIELD_ELEMENT_LENGTH();
    }

    public static FieldElement messageToFieldElement(byte[] message) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        BigInteger m = new BigInteger(digest.digest(message));
        BigInteger b = m.mod(new BigInteger("28948022309329048855892746252171976963322203655955319056773317069363642105857", 10));
        return FieldElement.deserialize(b.toByteArray());
    }

    public static FieldElement hashToFieldElement(String hexByte) {
        byte[] hashBytes = BytesUtils.fromHexString(hexByte);
        if (hashBytes.length > fieldElementLength()) {
            throw new IllegalArgumentException("Hash length is exceed Poseidon hash len. Hash len " +
                    hashBytes.length + " but it shall be " + fieldElementLength());
        }
        return FieldElement.deserialize(Arrays.copyOf(hashBytes, fieldElementLength()));
    }

    public static byte[] randomFieldElementBytes() {
        FieldElement fe = FieldElement.createRandom();
        byte[] feBytes = fe.serializeFieldElement();
        fe.freeFieldElement();
        return feBytes;
    }

    public static byte[] randomFieldElementBytes(long seed) {
        FieldElement fe = FieldElement.createRandom(seed);
        byte[] feBytes = fe.serializeFieldElement();
        fe.freeFieldElement();
        return feBytes;
    }
}
