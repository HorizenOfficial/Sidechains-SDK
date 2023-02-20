package com.horizen.cryptolibprovider.implementations;

import com.horizen.cryptolibprovider.VrfFunctions;
import com.horizen.cryptolibprovider.utils.FieldElementUtils;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.vrfnative.*;
import com.horizen.librustsidechains.Constants;

import java.util.EnumMap;
import java.util.Optional;

import static com.horizen.cryptolibprovider.VrfFunctions.KeyType.PUBLIC;
import static com.horizen.cryptolibprovider.VrfFunctions.KeyType.SECRET;

public class VrfFunctionsImplZendoo implements VrfFunctions {
    @Override
    public EnumMap<KeyType, byte[]> generatePublicAndSecretKeys(byte[] seed) {
        VRFKeyPair generated = VRFKeyPair.generate(seed);
        VRFSecretKey secretKey = generated.getSecretKey();
        VRFPublicKey publicKey = generated.getPublicKey();

        EnumMap<KeyType, byte[]> keysMap = new EnumMap<>(KeyType.class);
        keysMap.put(SECRET, secretKey.serializeSecretKey());
        keysMap.put(PUBLIC, publicKey.serializePublicKey());

        secretKey.freeSecretKey();
        publicKey.freePublicKey();

        return keysMap;
    }

    @Override
    public EnumMap<ProofType, byte[]> createProof(byte[] secretKeyBytes, byte[] publicKeyBytes, byte[] element) {
        VRFSecretKey secretKey = VRFSecretKey.deserialize(secretKeyBytes);
        VRFPublicKey publicKey = VRFPublicKey.deserialize(publicKeyBytes);

        VRFKeyPair keyPair = new VRFKeyPair(secretKey, publicKey);
        FieldElement fieldElement = FieldElementUtils.elementToFieldElement(element);
        VRFProveResult vrfProofAndVrfOutput = keyPair.prove(fieldElement);
        byte[] vrfProofBytes = vrfProofAndVrfOutput.getVRFProof().serializeProof();
        byte[] vrfOutputBytes = vrfProofAndVrfOutput.getVRFOutput().serializeFieldElement();

        EnumMap<ProofType, byte[]> proofsMap = new EnumMap<>(ProofType.class);
        proofsMap.put(ProofType.VRF_PROOF, vrfProofBytes);
        proofsMap.put(ProofType.VRF_OUTPUT, vrfOutputBytes);

        secretKey.freeSecretKey();
        publicKey.freePublicKey();
        vrfProofAndVrfOutput.getVRFProof().freeProof();
        vrfProofAndVrfOutput.getVRFOutput().freeFieldElement();
        fieldElement.freeFieldElement();

        return proofsMap;
    }

    @Override
    public boolean verifyProof(byte[] message, byte[] publicKeyBytes, byte[] proofBytes) {
        return proofToOutput(publicKeyBytes, message, proofBytes).isPresent();
    }

    @Override
    public boolean publicKeyIsValid(byte[] publicKeyBytes) {
        VRFPublicKey publicKey = VRFPublicKey.deserialize(publicKeyBytes);
        boolean keyIsValid = publicKey.verifyKey();
        publicKey.freePublicKey();

        return keyIsValid;
    }

    @Override
    public Optional<byte[]> proofToOutput(byte[] publicKeyBytes, byte[] element, byte[] proofBytes) {
        VRFPublicKey publicKey = VRFPublicKey.deserialize(publicKeyBytes);
        VRFProof vrfProof = VRFProof.deserialize(proofBytes);
        FieldElement messageAsFieldElement = FieldElementUtils.elementToFieldElement(element);

        // VRF public key, proof and field element could null in case they can not be deserialized.
        // It may happen when vrf public key is not valid or when fieldElement or proof is invalid.
        if(publicKey == null || vrfProof == null || messageAsFieldElement == null) {
            if(publicKey != null)
                publicKey.freePublicKey();
            if(vrfProof != null)
                vrfProof.freeProof();
            if(messageAsFieldElement != null)
                messageAsFieldElement.freeFieldElement();
            return Optional.empty();
        }

        FieldElement vrfOutput = publicKey.proofToHash(vrfProof, messageAsFieldElement);

        Optional<byte[]> output;
        if(vrfOutput != null) {
            output = Optional.of(vrfOutput.serializeFieldElement());
            vrfOutput.freeFieldElement();
        }
        else {
            output = Optional.empty();
        }

        publicKey.freePublicKey();
        vrfProof.freeProof();
        messageAsFieldElement.freeFieldElement();

        return output;
    }

    @Override
    public byte[] getPublicKey(byte[] secretKeyBytes) {
        VRFSecretKey secretKey = VRFSecretKey.deserialize(secretKeyBytes);
        VRFPublicKey publicKey = secretKey.getPublicKey();

        byte[] publicKeyBytes = publicKey.serializePublicKey();

        secretKey.freeSecretKey();
        publicKey.freePublicKey();

        return publicKeyBytes;
    }

    @Override
    public int vrfSecretKeyLength() {
        return Constants.VRF_SK_LENGTH();
    }

    @Override
    public int vrfPublicKeyLen() {
        return Constants.VRF_PK_LENGTH();
    }

    @Override
    public int vrfProofLen() {
        return Constants.VRF_PROOF_LENGTH();
    }

    @Override
    public int vrfOutputLen() {
        return Constants.FIELD_ELEMENT_LENGTH();
    }
}
