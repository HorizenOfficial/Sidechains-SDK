package com.horizen.customtypes;

import com.horizen.proposition.PropositionSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public class CustomMultiKeyPropositionSerializer implements PropositionSerializer<CustomMultiKeyProposition>
{
    private static CustomMultiKeyPropositionSerializer serializer;

    static {
        serializer = new CustomMultiKeyPropositionSerializer();
    }

    private CustomMultiKeyPropositionSerializer() {
        super();
    }

    public static CustomMultiKeyPropositionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(CustomMultiKeyProposition proposition, Writer writer) {
        writer.putBytes(proposition.pubKeyBytes());
    }

    @Override
    public CustomMultiKeyProposition parse(Reader reader) {
        return new CustomMultiKeyProposition(
                reader.getBytes(CustomMultiKeyProposition.SINGLE_PUBLIC_KEY_LENGTH),
                reader.getBytes(CustomMultiKeyProposition.SINGLE_PUBLIC_KEY_LENGTH)
        );
    }
}
