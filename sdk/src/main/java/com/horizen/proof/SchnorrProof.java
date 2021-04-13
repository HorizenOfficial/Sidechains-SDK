package com.horizen.proof;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.proposition.SchnorrProposition;
import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.secret.SchnorrSecret;
import com.horizen.serialization.Views;

import java.util.Arrays;
import java.util.Objects;

@JsonView(Views.Default.class)
public class SchnorrProof implements ProofOfKnowledge<SchnorrSecret, SchnorrProposition>
{
    private final byte[] signature;

    public SchnorrProof(byte[] signatureBytes) {
        Objects.requireNonNull(signatureBytes, "SchnorrProofBytes can't be null");

        signature = Arrays.copyOf(signatureBytes, signatureBytes.length);
    }

    @Override
    public boolean isValid(SchnorrProposition publicKey, byte[] message) {
        return CryptoLibProvider.schnorrFunctions().verify(message, publicKey.pubKeyBytes(), signature);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(signature, signature.length);
    }

    @Override
    public ProofSerializer serializer() {
        return SchnorrSignatureSerializer.getSerializer();
    }

    public static SchnorrProof parse(byte[] bytes) {
        return new SchnorrProof(bytes);
    }
}
