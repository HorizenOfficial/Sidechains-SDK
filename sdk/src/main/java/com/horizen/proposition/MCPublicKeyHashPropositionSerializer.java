package com.horizen.proposition;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class MCPublicKeyHashPropositionSerializer implements PropositionSerializer<MCPublicKeyHashProposition> {
    private static MCPublicKeyHashPropositionSerializer serializer;

    static {
        serializer = new MCPublicKeyHashPropositionSerializer();
    }

    private MCPublicKeyHashPropositionSerializer() {
        super();
    }

    public static MCPublicKeyHashPropositionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(MCPublicKeyHashProposition proposition, Writer writer) {
        writer.putBytes(proposition.pubKeyHashBytes);
    }

    @Override
    public MCPublicKeyHashProposition parse(Reader reader) {
        return new MCPublicKeyHashProposition(reader.getBytes(MCPublicKeyHashProposition.KEY_LENGTH));
    }
}
