package com.horizen.proposition;

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
        byte [] propositionBytes = proposition.bytes();
        writer.putInt(propositionBytes.length);
        writer.putBytes(proposition.bytes());
    }

    @Override
    public VrfPublicKey parse(Reader reader) {
        int length = reader.getInt();
        return VrfPublicKey.parseBytes(reader.getBytes(length));
    }
}
