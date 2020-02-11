package com.horizen.customtypes;

import com.horizen.proof.ProofSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class CustomProofSerializer implements ProofSerializer<CustomProof> {

    private static CustomProofSerializer serializer;

    static {
        serializer = new CustomProofSerializer();
    }

    private CustomProofSerializer() {
        super();
    }

    public static CustomProofSerializer getSerializer() {
        return serializer;
    }


    @Override
    public void serialize(CustomProof proof, Writer writer) {
        writer.putBytes(proof.bytes());
    }

    @Override
    public CustomProof parse(Reader reader) {
        return CustomProof.parseBytes(reader.getBytes(reader.remaining()));
    }
}
