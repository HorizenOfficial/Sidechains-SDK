package com.horizen.vrf;

import com.horizen.proof.VrfProofSerializer;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Serializer;
import scorex.util.serialization.Writer;

public class VrfProofHashSerializer implements ScorexSerializer<VrfProofHash> {
    private static VrfProofHashSerializer serializer;

    static {
        serializer = new VrfProofHashSerializer();
    }

    private VrfProofHashSerializer() {
        super();
    }

    public static VrfProofHashSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(VrfProofHash proofHash, Writer writer) {
        writer.putInt(proofHash.bytes().length);
        writer.putBytes(proofHash.bytes());
    }

    @Override
    public VrfProofHash parse(Reader reader) {
        int vrfProofHashLen = reader.getInt();
        return VrfProofHash.parse(reader.getBytes(vrfProofHashLen));
    }
}
