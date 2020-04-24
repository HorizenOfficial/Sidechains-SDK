package com.horizen.proof;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.proposition.SchnorrPublicKey;
import com.horizen.backwardtransfer.BackwardTransferLoader;
import com.horizen.secret.SchnorrSecretKey;
import com.horizen.serialization.Views;

import java.util.Arrays;
import java.util.Objects;

import static com.horizen.proof.CoreProofsIdsEnum.SchnorrSignatureId;

@JsonView(Views.Default.class)
@JsonIgnoreProperties("typeId")
public class SchnorrSignature implements ProofOfKnowledge<SchnorrSecretKey, SchnorrPublicKey>
{
    private final byte[] signature;

    public SchnorrSignature(byte[] signatureBytes) {
        Objects.requireNonNull(signatureBytes, "Public key can't be null");

        signature = Arrays.copyOf(signatureBytes, signatureBytes.length);
    }

    @Override
    public byte proofTypeId() {
        return SchnorrSignatureId.id();
    }

    @Override
    public boolean isValid(SchnorrPublicKey publicKey, byte[] message) {
        return BackwardTransferLoader.schnorrFunctions().verify(message, publicKey.pubKeyBytes(), signature);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(signature, signature.length);
    }

    @Override
    public ProofSerializer serializer() {
        return SchnorrSignatureSerializer.getSerializer();
    }

    public static SchnorrSignature parse(byte[] bytes) {
        return new SchnorrSignature(bytes);
    }
}
