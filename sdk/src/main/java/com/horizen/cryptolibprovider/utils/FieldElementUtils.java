package com.horizen.cryptolibprovider.utils;

import com.horizen.librustsidechains.FieldElement;
import com.horizen.librustsidechains.Constants;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.FieldElementsContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FieldElementUtils {
    public static int fieldElementLength() {
        return Constants.FIELD_ELEMENT_LENGTH();
    }

    public static FieldElement messageToFieldElement(byte[] message) {
        if (message.length > fieldElementLength()) {
            throw new IllegalArgumentException("Message length is exceed allowed message len. Message len " +
                    message.length + " but it shall be less than " + fieldElementLength());
        }
        return FieldElement.deserialize(Arrays.copyOf(message, fieldElementLength()));
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

    public static FieldElementsContainer deserializeMany(byte[] bytes, boolean strict) {
        List<FieldElement> deserializedFes = new ArrayList<>();
        int chunkSize = strict ? fieldElementLength() : fieldElementLength() - 1;
        int start = 0;
        while (start < bytes.length) {
            int end = Math.min(bytes.length, start + chunkSize);
            deserializedFes.add(FieldElement.deserialize(Arrays.copyOfRange(bytes, start, end)));
            start += chunkSize;
        }
        return new FieldElementsContainer(deserializedFes);
    }

    public static FieldElementsContainer deserializeMany(byte[] bytes) {
        return deserializeMany(bytes, false);
    }
}
