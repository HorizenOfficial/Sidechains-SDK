package com.horizen.proposition;

import com.horizen.cryptolibprovider.CryptoLibProvider;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class SchnorrPropositionSerializer implements PropositionSerializer<SchnorrProposition> {
    private static SchnorrPropositionSerializer serializer;

    static {
        serializer = new SchnorrPropositionSerializer();
    }

    private SchnorrPropositionSerializer() {
        super();
    }

    public static SchnorrPropositionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(SchnorrProposition proposition, Writer writer) {
        byte [] propositionBytes = proposition.bytes();
        writer.putBytes(propositionBytes);
    }

    @Override
    public SchnorrProposition parse(Reader reader) {
        return SchnorrProposition.parseBytes(reader.getBytes(CryptoLibProvider.schnorrFunctions().schnorrPublicKeyLength()));
    }
}
