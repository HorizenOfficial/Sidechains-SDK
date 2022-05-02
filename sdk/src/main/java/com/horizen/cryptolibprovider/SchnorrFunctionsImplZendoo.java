package com.horizen.cryptolibprovider;

import com.horizen.librustsidechains.FieldElement;
import com.horizen.schnorrnative.SchnorrKeyPair;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSecretKey;
import com.horizen.schnorrnative.SchnorrSignature;
import com.horizen.librustsidechains.Constants;

import java.util.EnumMap;

import static com.horizen.cryptolibprovider.FieldElementUtils.messageToFieldElement;
import static com.horizen.cryptolibprovider.SchnorrFunctions.KeyType.PUBLIC;
import static com.horizen.cryptolibprovider.SchnorrFunctions.KeyType.SECRET;

public class SchnorrFunctionsImplZendoo implements SchnorrFunctions {

    @Override
    public EnumMap<KeyType, byte[]> generateSchnorrKeys(byte[] seed) {
        SchnorrKeyPair keyPair = SchnorrKeyPair.generate();
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

        boolean signatureIsValid = publicKey.verifySignature(signature, fieldElement);

        signature.freeSignature();
        fieldElement.freeFieldElement();
        publicKey.freePublicKey();

        return signatureIsValid;
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
