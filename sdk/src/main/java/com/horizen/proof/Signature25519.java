package com.horizen.proof;

import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.utils.Ed25519;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import java.util.Arrays;

import static com.horizen.proof.CoreProofsIdsEnum.Signature25519Id;


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
    public byte proofTypeId() {
        return Signature25519Id.id();
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(signatureBytes, SIGNATURE_LENGTH);
    }

    @Override
    public ProofSerializer serializer() {
        return Signature25519Serializer.getSerializer();
    }

    public static Signature25519 parseBytes(byte[] bytes) {
        return new Signature25519(bytes);
    }

    @Override
    public String toString() {
        return "Signature25519{" +
                "signatureBytes=" + ByteUtils.toHexString(signatureBytes) +
                '}';
    }
}
