package com.horizen.cryptolibprovider;

import java.util.EnumMap;
import java.util.Optional;

public interface VrfFunctions {
    enum KeyType {
        SECRET,
        PUBLIC
    }

    enum ProofType {
        VRF_PROOF,
        VRF_OUTPUT
    }

    int vrfSecretKeyLength();

    int vrfPublicKeyLen();

    int vrfProofLen();

    int vrfOutputLen();

    EnumMap<KeyType, byte[]> generatePublicAndSecretKeys(byte[] seed);

    EnumMap<ProofType, byte[]> createProof(byte[] secretKeyBytes, byte[] publicKeyBytes, byte[] message);

    boolean verifyProof(byte[] message, byte[] publicKeyBytes, byte[] proofBytes);

    boolean publicKeyIsValid(byte[] publicKeyBytes);

    //Return Vrf proof hash for given proof / key / message; return None if proof is not valid
    Optional<byte[]> proofToOutput(byte[] publicKeyBytes, byte[] message, byte[] proofBytes);
}
