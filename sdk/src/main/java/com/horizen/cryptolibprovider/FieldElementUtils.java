package com.horizen.cryptolibprovider;

import com.horizen.librustsidechains.FieldElement;
import com.horizen.librustsidechains.Constants;
import com.horizen.utils.BytesUtils;

import java.util.Arrays;

public class FieldElementUtils {
    public static int maximumFieldElementLength() {
        return Constants.FIELD_ELEMENT_LENGTH();
    }

    static FieldElement messageToFieldElement(byte[] message) {
        if (message.length > maximumFieldElementLength()) {
            throw new IllegalArgumentException("Message length is exceed allowed message len. Message len " +
                    message.length + " but it shall be less than " + maximumFieldElementLength());
        }
        return FieldElement.deserialize(Arrays.copyOf(message, maximumFieldElementLength()));
    }

    public static FieldElement hashToFieldElement(String hexByte) {
        byte[] hashBytes = BytesUtils.fromHexString(hexByte);
        if (hashBytes.length > maximumFieldElementLength()) {
            throw new IllegalArgumentException("Hash length is exceed Poseidon hash len. Hash len " +
                    hashBytes.length + " but it shall be " + maximumFieldElementLength());
        }
        return FieldElement.deserialize(Arrays.copyOf(hashBytes, maximumFieldElementLength()));
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
