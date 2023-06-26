package io.horizen.proof;

import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public class SchnorrSignatureSerializer implements ProofSerializer<SchnorrProof> {
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
    public void serialize(SchnorrProof proof, Writer writer) { writer.putBytes(proof.signature); }

    @Override
    public SchnorrProof parse(Reader reader) {
        return new SchnorrProof(reader.getBytes(SchnorrProof.SIGNATURE_LENGTH));
    }
}