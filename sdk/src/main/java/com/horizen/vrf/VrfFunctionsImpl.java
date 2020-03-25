package com.horizen.vrf;


import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.utils.Utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class VrfFunctionsImpl implements VrfFunctions {
    private static int vrfLength = 32; //sha256HashLen
    private static byte[] consensusHardcodedSaltString = "TEST".getBytes();

    @Override
    public List<byte[]> generate(byte[] seed) {
        byte[] secretAndPublic = new byte[vrfLength];
        Arrays.fill(secretAndPublic, (byte) Objects.hash(new BigInteger(1, seed)));

        List<byte[]> res = new ArrayList<>();
        res.add(Arrays.copyOf(secretAndPublic, secretAndPublic.length));
        res.add(Arrays.copyOf(secretAndPublic, secretAndPublic.length));
        return res;
    }

    @Override
    public boolean verify(byte[] publicKey, int slotNumber, byte[] nonceBytes, byte[] proofBytes) {
        byte[] messageBytes = buildVrfMessageAsBytes(slotNumber, nonceBytes);
        byte[] messageWithCorrectLength = Utils.doubleSHA256Hash(messageBytes);

        byte[] decoded = Arrays.copyOf(proofBytes, proofBytes.length);
        for (int i = 0; i < decoded.length; i++) {
            decoded[i] = (byte) (decoded[i] ^ publicKey[0]);
        }
        return Arrays.equals(messageWithCorrectLength, decoded);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public byte[] proofBytesToVrfHashBytes(byte[] proof) {
        assert (proof.length == vrfLength);
        int xorByte = proof[0] ^ proof[proof.length - 1];

        byte[] vrfHash =  Arrays.copyOf(proof, proof.length);
        for (int i = 0; i < vrfHash.length; ++i) {
            vrfHash[i] = (byte) (vrfHash[i] ^ xorByte);
        }
        return vrfHash;
    }

    @Override
    public byte[] messageToVrfProofBytes(byte[] secretKey, int slotNumber, byte[] nonceBytes){
        byte[] messageBytes = buildVrfMessageAsBytes(slotNumber, nonceBytes);
        byte[] messageWithCorrectLength = Utils.doubleSHA256Hash(messageBytes);

        byte[] proofBytes = Arrays.copyOf(messageWithCorrectLength, messageWithCorrectLength.length);;
        for (int i = 0; i < proofBytes.length; ++i) {
            proofBytes[i] = (byte) (proofBytes[i] ^ secretKey[0]);
        }
        return proofBytes;
    }

    private static byte[] buildVrfMessageAsBytes(int slotNumber, byte[] nonceBytes) {
        byte[] slotNumberAsBytes = Ints.toByteArray(slotNumber);
        return Bytes.concat(slotNumberAsBytes, nonceBytes, consensusHardcodedSaltString);
    }

}
