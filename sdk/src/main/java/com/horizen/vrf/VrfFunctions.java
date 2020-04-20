package com.horizen.vrf;

import java.util.EnumMap;

public interface VrfFunctions {
    enum KeyType {
        SECRET,
        PUBLIC
    }

    EnumMap<KeyType, byte[]> generatePublicAndSecretKeys(byte[] seed);

    byte[] createVrfProofForMessage(byte[] secretKey, byte[] publicKey, byte[] message);

    boolean verifyMessage(byte[] message, byte[] publicKey, byte[] proofBytes);

    boolean publicKeyIsValid(byte[] publicKey);

    byte[] vrfProofToVrfHash(byte[] publicKey, byte[] message, byte[] proof);
}
