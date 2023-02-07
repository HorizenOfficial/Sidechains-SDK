package com.horizen.cryptolibprovider.implementations;

import com.horizen.cryptolibprovider.utils.SchnorrFunctions;
import com.horizen.librustsidechains.Constants;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.schnorrnative.SchnorrKeyPair;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSecretKey;
import com.horizen.schnorrnative.SchnorrSignature;
import java.util.EnumMap;
import static com.horizen.cryptolibprovider.utils.FieldElementUtils.messageToFieldElement;
import static com.horizen.cryptolibprovider.utils.SchnorrFunctions.KeyType.PUBLIC;
import static com.horizen.cryptolibprovider.utils.SchnorrFunctions.KeyType.SECRET;

public class SchnorrFunctionsImplZendoo implements SchnorrFunctions {

    @Override
    public EnumMap<KeyType, byte[]> generateSchnorrKeys(byte[] seed) {
        SchnorrKeyPair keyPair = SchnorrKeyPair.generate(seed);
        SchnorrSecretKey secretKey = keyPair.getSecretKey();
        SchnorrPublicKey publicKey = keyPair.getPublicKey();

        EnumMap<KeyType, byte[]> keysMap = new EnumMap<>(KeyType.class);
        keysMap.put(SECRET, secretKey.serializeSecretKey());
        keysMap.put(PUBLIC, publicKey.serializePublicKey());

        secretKey.freeSecretKey();
        publicKey.freePublicKey();

        return keysMap;
    }

    @Override
    public byte[] sign(byte[] secretKeyBytes, byte[] publicKeyBytes, byte[] messageBytes) {
        SchnorrSecretKey secretKey = SchnorrSecretKey.deserialize(secretKeyBytes); // TODO: why there is no `checkPublicKey` flag for PrivateKey?
        SchnorrPublicKey publicKey = SchnorrPublicKey.deserialize(publicKeyBytes);

        SchnorrKeyPair keyPair = new SchnorrKeyPair(secretKey, publicKey);
        FieldElement fieldElement = messageToFieldElement(messageBytes);
        SchnorrSignature signature = keyPair.signMessage(fieldElement);
        byte[] signatureBytes = signature.serializeSignature();

        signature.freeSignature();
        fieldElement.freeFieldElement();
        publicKey.freePublicKey();
        secretKey.freeSecretKey();

        return signatureBytes;
    }

    @Override
    public boolean verify(byte[] messageBytes, byte[] publicKeyBytes, byte[] signatureBytes) {
        SchnorrPublicKey publicKey = SchnorrPublicKey.deserialize(publicKeyBytes);
        FieldElement fieldElement = messageToFieldElement(messageBytes);
        SchnorrSignature signature = SchnorrSignature.deserialize(signatureBytes);

        // Schnorr public key, signature and field element could null in case they can not be deserialized.
        // It may happen when schnorr public key is not valid or when fieldElement or signature is invalid.
        if(publicKey == null || fieldElement == null || signature == null) {
            if(publicKey != null)
                publicKey.freePublicKey();
            if(fieldElement != null)
                fieldElement.freeFieldElement();
            if(signature != null)
                signature.freeSignature();
            return false;
        }

        boolean signatureIsValid = publicKey.verifySignature(signature, fieldElement);

        signature.freeSignature();
        fieldElement.freeFieldElement();
        publicKey.freePublicKey();

        return signatureIsValid;
    }

    @Override
    public byte[] getHash(byte[] publicKeyBytes) {
        SchnorrPublicKey publicKey = SchnorrPublicKey.deserialize(publicKeyBytes);
        FieldElement hash = publicKey.getHash();
        byte[] hashBytes = hash.serializeFieldElement();

        publicKey.freePublicKey();
        hash.freeFieldElement();

        return hashBytes;
    }

    @Override
    public byte[] getPublicKey(byte[] secretKeyBytes) {
        SchnorrSecretKey secretKey = SchnorrSecretKey.deserialize(secretKeyBytes);
        SchnorrPublicKey publicKey = secretKey.getPublicKey();

        byte[] publicKeyBytes = publicKey.serializePublicKey();

        secretKey.freeSecretKey();
        publicKey.freePublicKey();

        return publicKeyBytes;
    }

    @Override
    public int schnorrSecretKeyLength() {
        return Constants.SCHNORR_SK_LENGTH();
    }

    @Override
    public int schnorrPublicKeyLength(){
        return Constants.SCHNORR_PK_LENGTH();
    }

    @Override
    public int schnorrSignatureLength() {
        return Constants.SCHNORR_SIGNATURE_LENGTH();
    }

}
