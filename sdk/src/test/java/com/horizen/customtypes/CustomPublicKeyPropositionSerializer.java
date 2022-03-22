package com.horizen.customtypes;

import com.horizen.proposition.PropositionSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class CustomPublicKeyPropositionSerializer implements PropositionSerializer<CustomPublicKeyProposition>
{
    private static CustomPublicKeyPropositionSerializer serializer;

    static {
        serializer = new CustomPublicKeyPropositionSerializer();
    }

    private CustomPublicKeyPropositionSerializer() {
        super();
    }

    public static CustomPublicKeyPropositionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(CustomPublicKeyProposition proposition, Writer writer) {
        writer.putBytes(proposition.pubKeyBytes());
    }

    @Override
    public CustomPublicKeyProposition parse(Reader reader) {
        return new CustomPublicKeyProposition(reader.getBytes(CustomPublicKeyProposition.PUBLIC_KEY_LENGTH));
    }
}
