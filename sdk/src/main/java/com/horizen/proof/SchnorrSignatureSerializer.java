package com.horizen.proof;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class SchnorrSignatureSerializer implements ProofSerializer<SchnorrSignature> {
    private static SchnorrSignatureSerializer serializer;

    static {
        serializer = new SchnorrSignatureSerializer();
    }

    private SchnorrSignatureSerializer() {
        super();
    }

    public static SchnorrSignatureSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(SchnorrSignature proof, Writer writer) {
        writer.putBytes(proof.bytes());
    }

    @Override
    public SchnorrSignature parse(Reader reader) {
        return SchnorrSignature.parse(reader.getBytes(reader.remaining()));
    }
}