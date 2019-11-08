package com.horizen.proposition;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class MCPublicKeyHashSerializer implements PropositionSerializer<MCPublicKeyHash> {
    private static MCPublicKeyHashSerializer serializer;

    static {
        serializer = new MCPublicKeyHashSerializer();
    }

    private MCPublicKeyHashSerializer() {
        super();
    }

    public static MCPublicKeyHashSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(MCPublicKeyHash proposition, Writer writer) {
        writer.putBytes(proposition.bytes());
    }

    @Override
    public MCPublicKeyHash parse(Reader reader) {
        return MCPublicKeyHash.parseBytes(reader.getBytes(reader.remaining()));
    }
}
