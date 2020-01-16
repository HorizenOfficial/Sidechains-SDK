package com.horizen.vrf;

import com.horizen.box.RegularBoxSerializer;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class VRFProofSerializer
    implements ScorexSerializer<VRFProof>
{

    private static VRFProofSerializer serializer;

    static {
        serializer = new VRFProofSerializer();
    }

    public static VRFProofSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(VRFProof proof, Writer writer) {
        writer.putBytes(proof.bytes());
    }

    @Override
    public VRFProof parse(Reader reader) {
        return VRFProof.parseBytes(reader.getBytes(reader.remaining()));
    }
}
