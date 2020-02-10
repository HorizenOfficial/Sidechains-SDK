package com.horizen.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.serialization.Views;
import com.horizen.utils.Ed25519;

import java.util.Arrays;
import java.util.Objects;

@JsonView(Views.Default.class)
public abstract class AbstractSignature25519<S extends PrivateKey25519, P extends ProofOfKnowledgeProposition<S>>
        implements ProofOfKnowledge<S, P> {

    public static int SIGNATURE_LENGTH = Ed25519.signatureLength();

    @JsonProperty("signature")
    protected final byte[] signatureBytes;

    public AbstractSignature25519(byte[] signatureBytes) {
        if (signatureBytes.length != SIGNATURE_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect signature length, %d expected, %d found", SIGNATURE_LENGTH,
                    signatureBytes.length));

        this.signatureBytes = Arrays.copyOf(signatureBytes, SIGNATURE_LENGTH);
    }

    @Override
    public boolean isValid(P proposition, byte[] message) {
        return Ed25519.verify(signatureBytes, message, proposition.pubKeyBytes());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractSignature25519 that = (AbstractSignature25519) o;
        return SIGNATURE_LENGTH == AbstractSignature25519.SIGNATURE_LENGTH &&
                Arrays.equals(signatureBytes, that.signatureBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(SIGNATURE_LENGTH);
        result = 31 * result + Arrays.hashCode(signatureBytes);
        return result;
    }
}
