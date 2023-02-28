package io.horizen.proof;

import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.secret.PrivateKey25519;
import io.horizen.utils.BytesUtils;
import io.horizen.utils.Ed25519;


public final class Signature25519 extends AbstractSignature25519<PrivateKey25519, PublicKey25519Proposition>
{
    public static int SIGNATURE_LENGTH = Ed25519.signatureLength();

    public Signature25519(byte[] signatureBytes) {
        super(signatureBytes);
        if (signatureBytes.length != SIGNATURE_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect signature length, %d expected, %d found", SIGNATURE_LENGTH,
                    signatureBytes.length));
    }

    @Override
    public ProofSerializer serializer() {
        return Signature25519Serializer.getSerializer();
    }

    @Override
    public String toString() {
        return "Signature25519{" +
                "signatureBytes=" + BytesUtils.toHexString(signatureBytes) +
                '}';
    }
}
