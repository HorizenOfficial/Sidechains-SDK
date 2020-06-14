package com.horizen.proof;

import com.horizen.cryptolibprovider.CryptoLibProvider;
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
    public void serialize(VrfProof proof, Writer writer) {
        writer.putBytes(proof.bytes());
    }

    @Override
    public VrfProof parse(Reader reader) {
        return VrfProof.parse(reader.getBytes(CryptoLibProvider.vrfFunctions().vrfProofLen()));
    }
}
