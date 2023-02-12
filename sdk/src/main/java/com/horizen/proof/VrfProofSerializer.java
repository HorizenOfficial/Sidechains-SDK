package com.horizen.proof;

import com.horizen.utils.Checker;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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
    public void serialize(VrfProof proof, Writer writer) {
        writer.putBytes(proof.proofBytes);
    }

    @Override
    public VrfProof parse(Reader reader) {
        byte[] vrfProof = Checker.readBytes(reader, VrfProof.PROOF_LENGTH, "VRF proof");
        return new VrfProof(vrfProof);
    }
}
