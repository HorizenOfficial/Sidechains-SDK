package io.horizen.cryptolibprovider;

import io.horizen.librustsidechains.FieldElement;

import java.util.EnumMap;

public interface SchnorrFunctions {
    enum KeyType {
        SECRET,
        PUBLIC
    }

    EnumMap<KeyType, byte[]> generateSchnorrKeys(byte[] seed);

    //message is taken form createBackwardTransferMessage
    byte[] sign(byte[] secretKeyBytes, byte[] publicKeyBytes, byte[] messageBytes);

    //looks like is not used at all in SDK
    boolean verify(byte[] messageBytes, byte[] publicKeyBytes, byte[] signatureBytes);

    byte[] getHash(byte[] publicKeyBytes);

    byte[] getPublicKey(byte[] secretKeyBytes);

    int schnorrSecretKeyLength();

    int schnorrPublicKeyLength();

    int schnorrSignatureLength();

    boolean publicKeyIsValid(byte[] propositionBytes);
}