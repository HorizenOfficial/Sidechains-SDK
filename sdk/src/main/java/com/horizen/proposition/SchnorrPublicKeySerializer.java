package com.horizen.proposition;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class SchnorrPublicKeySerializer implements PropositionSerializer<SchnorrPublicKey> {
    private static SchnorrPublicKeySerializer serializer;

    static {
        serializer = new SchnorrPublicKeySerializer();
    }

    private SchnorrPublicKeySerializer() {
        super();
    }

    public static SchnorrPublicKeySerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(SchnorrPublicKey proposition, Writer writer) {
        byte [] propositionBytes = proposition.bytes();
        writer.putBytes(propositionBytes);
    }

    @Override
    public SchnorrPublicKey parse(Reader reader) {
        return SchnorrPublicKey.parseBytes(reader.getBytes(reader.remaining()));
    }
}
