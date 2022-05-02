package com.horizen.cryptolibprovider;


import com.horizen.utils.Utils;

import java.math.BigInteger;
import java.util.*;

public class DummyVrfFunctionsImpl implements VrfFunctions {
    private static int vrfLength = 32; //sha256HashLen

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
    public boolean verifyProof(byte[] messageBytes, byte[] publicKeyBytes, byte[] proofBytes) {
        byte[] messageWithCorrectLength = Utils.doubleSHA256Hash(messageBytes);

        byte[] decoded = Arrays.copyOf(proofBytes, proofBytes.length);
        for (int i = 0; i < decoded.length; i++) {
            decoded[i] = (byte) (decoded[i] ^ publicKeyBytes[0]);
        }
        //System.out.println("check message: " + ByteUtils.toHexString(messageBytes) + " by proof:" + ByteUtils.toHexString(proofBytes) + " by public key: " +ByteUtils.toHexString(publicKey));

        return Arrays.equals(messageWithCorrectLength, decoded);
    }

    @Override
    public boolean publicKeyIsValid(byte[] publicKeyBytes) {
        return publicKeyBytes.length == vrfLength;
    }

    @Override
    public Optional<byte[]> proofToOutput(byte[] publicKeyBytes, byte[] message, byte[] proofBytes) {
        assert (proofBytes.length == vrfLength);
        int xorByte = proofBytes[0] ^ proofBytes[proofBytes.length - 1];

        if (verifyProof(message, publicKeyBytes, proofBytes)) {
            byte[] vrfOutput =  Arrays.copyOf(proofBytes, proofBytes.length);
            for (int i = 0; i < vrfOutput.length; ++i) {
                vrfOutput[i] = (byte) (vrfOutput[i] ^ xorByte);
            }
            return Optional.of(vrfOutput);
        }
        else {
            return Optional.empty();
        }
    }

    @Override
    public EnumMap<ProofType, byte[]> createProof(byte[] secretKeyBytes, byte[] publicKeyBytes, byte[] message){
        byte[] messageWithCorrectLength = Utils.doubleSHA256Hash(message);

        byte[] proofBytes = Arrays.copyOf(messageWithCorrectLength, messageWithCorrectLength.length);;
        for (int i = 0; i < proofBytes.length; ++i) {
            proofBytes[i] = (byte) (proofBytes[i] ^ secretKeyBytes[0]);
        }

        byte[] vrfOutputBytes = proofToOutput(publicKeyBytes, message, proofBytes).get();

        EnumMap<ProofType, byte[]> proofsMap = new EnumMap<>(ProofType.class);
        proofsMap.put(ProofType.VRF_PROOF, proofBytes);
        proofsMap.put(ProofType.VRF_OUTPUT, vrfOutputBytes);
        //System.out.println("For message:" + ByteUtils.toHexString(message) + "create proof: " + ByteUtils.toHexString(proofBytes) + " by public key: " + ByteUtils.toHexString(publicKey));
        return proofsMap;
    }

    @Override
    public int vrfSecretKeyLength() {
        return vrfLength;
    }

    @Override
    public int vrfPublicKeyLen() {
        return vrfLength;
    }

    @Override
    public int vrfProofLen() {
        return vrfLength;
    }

    @Override
    public int vrfOutputLen() {
        return vrfLength;
    }
}
