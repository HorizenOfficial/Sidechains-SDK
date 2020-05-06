package com.horizen.vrf;

import java.util.Arrays;
import java.util.EnumMap;

import com.google.common.primitives.Longs;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.vrfnative.*;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

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
    public byte[] createVrfProof(byte[] secretKey, byte[] publicKey, byte[] message) {
        VRFSecretKey sKey = VRFSecretKey.deserialize(secretKey);
        VRFPublicKey pKey = VRFPublicKey.deserialize(publicKey);

        VRFKeyPair keyPair = new VRFKeyPair(sKey, pKey);
        FieldElement fieldElement = messageToFieldElement(message);
        VRFProveResult vrfProofAndVrfProofHash = keyPair.prove(fieldElement);
        byte[] vrfProofBytes = vrfProofAndVrfProofHash.getVRFProof().serializeProof();

        sKey.freeSecretKey();
        pKey.freePublicKey();
        vrfProofAndVrfProofHash.getVRFProof().freeProof();
        vrfProofAndVrfProofHash.getVRFOutput().freeFieldElement();
        fieldElement.freeFieldElement();

        return vrfProofBytes;
    }

    @Override
    public boolean verifyProof(byte[] message, byte[] publicKey, byte[] proofBytes) {
        VRFPublicKey pKey = VRFPublicKey.deserialize(publicKey);
        VRFProof vrfProof = VRFProof.deserialize(proofBytes);
        FieldElement messageAsFieldElement = messageToFieldElement(message);

        FieldElement proofHash = pKey.proofToHash(vrfProof, messageAsFieldElement);

        pKey.freePublicKey();
        vrfProof.freeProof();
        messageAsFieldElement.freeFieldElement();

        if (proofHash != null) {
            proofHash.freeFieldElement();
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public boolean publicKeyIsValid(byte[] publicKey) {
        VRFPublicKey pKey = VRFPublicKey.deserialize(publicKey);
        boolean keyIsValid = pKey.verifyKey();
        pKey.freePublicKey();

        return keyIsValid;
    }

    @Override
    public byte[] vrfProofToVrfHash(byte[] publicKey, byte[] message, byte[] proof) {
        VRFPublicKey pKey = VRFPublicKey.deserialize(publicKey);
        VRFProof vrfProof = VRFProof.deserialize(proof);
        FieldElement messageAsFieldElement = messageToFieldElement(message);

        FieldElement proofHash = pKey.proofToHash(vrfProof, messageAsFieldElement);
        byte[] proofHashBytes = proofHash.serializeFieldElement();

        pKey.freePublicKey();
        vrfProof.freeProof();
        messageAsFieldElement.freeFieldElement();
        proofHash.freeFieldElement();

        return proofHashBytes;
    }

    //Temporarily solution, looks like as input consensus related parameters shall be passed for Field element creation
    private FieldElement messageToFieldElement(byte[] message) {
        byte[] longSrc = Arrays.copyOf(message, Longs.BYTES);

        int start = Longs.BYTES;
        while(start < message.length) {
            byte[] toXor = new byte[Longs.BYTES];
            System.arraycopy(message, start, toXor, 0, Math.min(Longs.BYTES, (message.length - start)));
            longSrc = ByteUtils.xor(longSrc, toXor);
            start += Longs.BYTES;
        }

        long longForFieldElement = Longs.fromByteArray(longSrc);
        FieldElement fieldElement = FieldElement.createFromLong(longForFieldElement);
        return fieldElement;
    }
}
