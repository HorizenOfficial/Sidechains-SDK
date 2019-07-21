package com.horizen.customtypes;

import com.horizen.proposition.PropositionSerializer;
import scala.util.Try;
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
        writer.putBytes(proposition.bytes());
    }

    @Override
    public CustomPublicKeyProposition parse(Reader reader) {
        return CustomPublicKeyProposition.parseBytes(reader.getBytes(reader.remaining())).get();
    }
}
