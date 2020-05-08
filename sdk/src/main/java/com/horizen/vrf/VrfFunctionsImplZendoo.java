package com.horizen.vrf;

import com.google.common.primitives.Longs;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.vrfnative.*;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Optional;

import static com.horizen.vrf.VrfFunctions.KeyType.PUBLIC;
import static com.horizen.vrf.VrfFunctions.KeyType.SECRET;

public class VrfFunctionsImplZendoo implements VrfFunctions {
    //@TODO Seed shall be supported from JNI side
    @Override
    public EnumMap<KeyType, byte[]> generatePublicAndSecretKeys(byte[] seed) {
        VRFKeyPair generated = VRFKeyPair.generate();
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
    public EnumMap<ProofType, byte[]> createVrfProof(byte[] secretKeyBytes, byte[] publicKeyBytes, byte[] message) {
        VRFSecretKey secretKey = VRFSecretKey.deserialize(secretKeyBytes);
        VRFPublicKey publicKey = VRFPublicKey.deserialize(publicKeyBytes);

        VRFKeyPair keyPair = new VRFKeyPair(secretKey, publicKey);
        FieldElement fieldElement = messageToFieldElement(message);
        VRFProveResult vrfProofAndVrfOutput = keyPair.prove(fieldElement);
        byte[] vrfProofBytes = vrfProofAndVrfOutput.getVRFProof().serializeProof();
        byte[] vrfOutputBytes = vrfProofAndVrfOutput.getVRFOutput().serializeFieldElement();

        EnumMap<ProofType, byte[]> proofsMap = new EnumMap<>(ProofType.class);
        proofsMap.put(ProofType.VRF_PROOF, vrfProofBytes);
        proofsMap.put(ProofType.VRF_PROOF_OUTPUT, vrfOutputBytes);

        secretKey.freeSecretKey();
        publicKey.freePublicKey();
        vrfProofAndVrfOutput.getVRFProof().freeProof();
        vrfProofAndVrfOutput.getVRFOutput().freeFieldElement();
        fieldElement.freeFieldElement();

        return proofsMap;
    }

    @Override
    public boolean verifyProof(byte[] message, byte[] publicKeyBytes, byte[] proofBytes) {
        VRFPublicKey publicKey = VRFPublicKey.deserialize(publicKeyBytes);
        VRFProof vrfProof = VRFProof.deserialize(proofBytes);
        FieldElement messageAsFieldElement = messageToFieldElement(message);

        FieldElement vrfOutput = publicKey.proofToHash(vrfProof, messageAsFieldElement);

        publicKey.freePublicKey();
        vrfProof.freeProof();
        messageAsFieldElement.freeFieldElement();

        if (vrfOutput != null) {
            vrfOutput.freeFieldElement();
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public boolean publicKeyIsValid(byte[] publicKeyBytes) {
        VRFPublicKey publicKey = VRFPublicKey.deserialize(publicKeyBytes);
        boolean keyIsValid = publicKey.verifyKey();
        publicKey.freePublicKey();

        return keyIsValid;
    }

    @Override
    public Optional<byte[]> vrfProofToVrfOutput(byte[] publicKeyBytes, byte[] message, byte[] proofBytes) {
        VRFPublicKey publicKey = VRFPublicKey.deserialize(publicKeyBytes);
        VRFProof vrfProof = VRFProof.deserialize(proofBytes);
        FieldElement messageAsFieldElement = messageToFieldElement(message);

        FieldElement vrfOutput = publicKey.proofToHash(vrfProof, messageAsFieldElement);

        if (vrfOutput != null) {
            byte[] vrfOutputBytes = vrfOutput.serializeFieldElement();

            publicKey.freePublicKey();
            vrfProof.freeProof();
            messageAsFieldElement.freeFieldElement();
            vrfOutput.freeFieldElement();

            return Optional.of(vrfOutputBytes);
        }
        else {
            return Optional.empty();
        }
    }

    @Override
    public int maximumVrfMessageLength() {
        return FieldElement.FIELD_ELEMENT_LENGTH;
    }

    private FieldElement messageToFieldElement(byte[] message) {
        if (message.length >= maximumVrfMessageLength()) {
            throw new IllegalArgumentException("Message length is exceed allowed message len. Message len " +
                    message.length + " but it shall be less than " + maximumVrfMessageLength());
        }
        return FieldElement.deserialize(Arrays.copyOf(message, maximumVrfMessageLength()));
    }
}
