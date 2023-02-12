package com.horizen.proposition;

import com.horizen.utils.Checker;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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
        byte[] proposition = Checker.readBytes(reader, MCPublicKeyHashProposition.KEY_LENGTH, "public key hash");
        return new MCPublicKeyHashProposition(proposition);
    }
}
