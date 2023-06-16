package io.horizen.cryptolibprovider.utils;

import com.horizen.librustsidechains.Constants;
import com.horizen.librustsidechains.FieldElement;
import io.horizen.utils.FieldElementsContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FieldElementUtils {
    public static int fieldElementLength() {
        return Constants.FIELD_ELEMENT_LENGTH();
    }

    public static FieldElement messageToFieldElement(byte[] message) {
        if (message.length != fieldElementLength()) {
            throw new IllegalArgumentException("Unexpected message length. Message len " +
                    message.length + " but it should be equal to " + fieldElementLength());
        }
        return FieldElement.deserialize(message);
    }

    public static FieldElement elementToFieldElement(byte[] element) {
        if (element.length > fieldElementLength()) {
            throw new IllegalArgumentException("Element length is exceed allowed element len. Field len " +
                    element.length + " but it shall be less than " + fieldElementLength());
        }
        return FieldElement.deserialize(Arrays.copyOf(element, fieldElementLength()));
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
