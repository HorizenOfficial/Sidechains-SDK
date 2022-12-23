package com.horizen.cryptolibprovider.utils;

import com.horizen.librustsidechains.FieldElement;

import java.util.EnumMap;

public interface SchnorrFunctions {
    enum KeyType {
        SECRET,
        PUBLIC
    }

    //JNI doesn't support seed at this moment
    EnumMap<KeyType, byte[]> generateSchnorrKeys(byte[] seed);

    //message is taken form createBackwardTransferMessage
    byte[] sign(byte[] secretKeyBytes, byte[] publicKeyBytes, byte[] messageBytes);

    //looks like is not used at all in SDK
    boolean verify(byte[] messageBytes, byte[] publicKeyBytes, byte[] signatureBytes);

    byte[] getHash(byte[] publicKeyBytes);

    int schnorrSecretKeyLength();

    int schnorrPublicKeyLength();

    int schnorrSignatureLength();

}
