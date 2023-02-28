package io.horizen.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.proposition.SchnorrProposition;
import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.secret.SchnorrSecret;
import com.horizen.json.Views;

import java.util.Arrays;

@JsonView(Views.Default.class)
public class SchnorrProof implements ProofOfKnowledge<SchnorrSecret, SchnorrProposition> {
    public static int SIGNATURE_LENGTH = CryptoLibProvider.schnorrFunctions().schnorrSignatureLength();

    @JsonProperty("signature")
    final byte[] signature;

    public SchnorrProof(byte[] signatureBytes) {
        if (signatureBytes.length != SIGNATURE_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect signature length, %d expected, %d found", SIGNATURE_LENGTH,
                    signatureBytes.length));

        signature = Arrays.copyOf(signatureBytes, signatureBytes.length);
    }

    @Override
    public boolean isValid(SchnorrProposition publicKey, byte[] message) {
        return CryptoLibProvider.schnorrFunctions().verify(message, publicKey.pubKeyBytes(), signature);
    }

    @Override
    public ProofSerializer serializer() {
        return SchnorrSignatureSerializer.getSerializer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchnorrProof that = (SchnorrProof) o;
        return Arrays.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(signature);
    }
}
