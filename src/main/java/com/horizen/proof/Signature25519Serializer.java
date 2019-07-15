package com.horizen.proof;

import scala.util.Try;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

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
    public byte[] toBytes(Signature25519 signature) {
        return signature.bytes();
    }

    @Override
    public Try<Signature25519> parseBytesTry(byte[] bytes) {
        return Signature25519.parseBytes(bytes);
    }

    @Override
    public void serialize(Signature25519 obj, Writer writer) {

    }

    @Override
    public Signature25519 parse(Reader reader) {
        return null;
    }
}
