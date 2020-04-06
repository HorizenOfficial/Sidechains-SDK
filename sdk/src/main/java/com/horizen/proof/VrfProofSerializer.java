package com.horizen.proof;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class VrfProofSerializer implements ProofSerializer<VrfProof> {
    private static VrfProofSerializer serializer;

    static {
        serializer = new VrfProofSerializer();
    }

    private VrfProofSerializer() {
        super();
    }

    public static VrfProofSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(VrfProof signature, Writer writer) {
        writer.putInt(signature.bytes().length);
        writer.putBytes(signature.bytes());
    }

    @Override
    public VrfProof parse(Reader reader) {
        int vrfProofLen = reader.getInt();
        return VrfProof.parse(reader.getBytes(vrfProofLen));
    }
}
