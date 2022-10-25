package com.horizen.cryptolibprovider;

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

    int schnorrSecretKeyLength();

    int schnorrPublicKeyLength();

    int schnorrSignatureLength();

    //TODO: this is a temporary function used for the development. It MUST be replaced with the real one in the cryptolib.
    FieldElement publicKeyToFieldElement(byte[] publicKey);
}
