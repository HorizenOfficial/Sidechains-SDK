package com.horizen.proof;

import io.circe.Json;
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
    public void serialize(Signature25519 signature, Writer writer) {
        writer.putBytes(signature.bytes());
    }

    @Override
    public Signature25519 parse(Reader reader) {
        return Signature25519.parseBytes(reader.getBytes(reader.remaining())).get();
    }

    @Override
    public Signature25519 parseJson(Json json) {
        return Signature25519.parseJson(json);
    }

}
