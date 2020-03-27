package com.horizen.vrf;

import com.horizen.proposition.PropositionSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class VrfPublicKeySerializer implements PropositionSerializer<VrfPublicKey> {
    private static VrfPublicKeySerializer serializer;

    static {
        serializer = new VrfPublicKeySerializer();
    }

    private VrfPublicKeySerializer() {
        super();
    }

    public static VrfPublicKeySerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(VrfPublicKey proposition, Writer writer) {
        writer.putBytes(proposition.bytes());
    }

    @Override
    public VrfPublicKey parse(Reader reader) {
        return VrfPublicKey.parseBytes(reader.getBytes(reader.remaining()));
    }
}
