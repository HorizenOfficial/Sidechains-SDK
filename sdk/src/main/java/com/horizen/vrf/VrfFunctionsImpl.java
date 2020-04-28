package com.horizen.vrf;


import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.utils.Utils;

import java.math.BigInteger;
import java.util.*;

public class VrfFunctionsImpl implements VrfFunctions {
    private static int vrfLength = 32; //sha256HashLen
    private static byte[] consensusHardcodedSaltString = "TEST".getBytes();

    @Override
    public EnumMap<KeyType, byte[]> generatePublicAndSecretKeys(byte[] seed) {
        byte[] secretAndPublic = new byte[vrfLength];
        Arrays.fill(secretAndPublic, (byte) Objects.hash(new BigInteger(1, seed)));

        EnumMap<KeyType, byte[]> res = new EnumMap<>(KeyType.class);
        res.put(KeyType.PUBLIC, Arrays.copyOf(secretAndPublic, secretAndPublic.length));
        res.put(KeyType.SECRET, Arrays.copyOf(secretAndPublic, secretAndPublic.length));
        return res;
    }

    @Override
    public boolean verifyProof(byte[] messageBytes, byte[] publicKey, byte[] proofBytes) {
        byte[] messageWithCorrectLength = Utils.doubleSHA256Hash(messageBytes);

        byte[] decoded = Arrays.copyOf(proofBytes, proofBytes.length);
        for (int i = 0; i < decoded.length; i++) {
            decoded[i] = (byte) (decoded[i] ^ publicKey[0]);
        }
        return Arrays.equals(messageWithCorrectLength, decoded);
    }

    @Override
    public boolean publicKeyIsValid(byte[] publicKey) {
        return publicKey.length == vrfLength;
    }

    @Override
    public byte[] vrfProofToVrfHash(byte[] publicKey, byte[] message, byte[] proof) {
        assert (proof.length == vrfLength);
        int xorByte = proof[0] ^ proof[proof.length - 1];

        byte[] vrfHash =  Arrays.copyOf(proof, proof.length);
        for (int i = 0; i < vrfHash.length; ++i) {
            vrfHash[i] = (byte) (vrfHash[i] ^ xorByte);
        }
        return vrfHash;
    }

    @Override
    public byte[] createVrfProof(byte[] secretKey, byte[] publicKey, byte[] message){
        byte[] messageWithCorrectLength = Utils.doubleSHA256Hash(message);

        byte[] proofBytes = Arrays.copyOf(messageWithCorrectLength, messageWithCorrectLength.length);;
        for (int i = 0; i < proofBytes.length; ++i) {
            proofBytes[i] = (byte) (proofBytes[i] ^ secretKey[0]);
        }
        return proofBytes;
    }
}
