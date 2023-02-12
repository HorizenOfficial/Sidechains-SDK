package com.horizen.proof;

import com.horizen.utils.Checker;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public final class Signature25519Serializer implements ProofSerializer<Signature25519> {

    private static Signature25519Serializer serializer;

    static {
        serializer = new Signature25519Serializer();
    }

    private Signature25519Serializer() {
        super();
    }

    public static Signature25519Serializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(Signature25519 signature, Writer writer) {
        writer.putBytes(signature.signatureBytes);
    }

    @Override
    public Signature25519 parse(Reader reader) {
        byte[] signature = Checker.readBytes(reader, Signature25519.SIGNATURE_LENGTH, "Signature25519");
        return new Signature25519(signature);
    }

}
