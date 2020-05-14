package com.horizen.zendoocryptolib;

import com.horizen.librustsidechains.FieldElement;

import java.util.Arrays;

public class FieldElementUtils {
    public static int maximumVrfMessageLength() {
        return FieldElement.FIELD_ELEMENT_LENGTH;
    }

    static FieldElement messageToFieldElement(byte[] message) {
        if (message.length >= maximumVrfMessageLength()) {
            throw new IllegalArgumentException("Message length is exceed allowed message len. Message len " +
                    message.length + " but it shall be less than " + maximumVrfMessageLength());
        }
        return FieldElement.deserialize(Arrays.copyOf(message, maximumVrfMessageLength()));
    }



}
