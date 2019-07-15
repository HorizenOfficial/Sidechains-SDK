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
    public byte[] toBytes(CustomPublicKeyProposition obj) {
        return obj.bytes();
    }

    @Override
    public Try<CustomPublicKeyProposition> parseBytesTry(byte[] bytes) {
        return CustomPublicKeyProposition.parseBytes(bytes);
    }

    @Override
    public void serialize(CustomPublicKeyProposition obj, Writer writer) {

    }

    @Override
    public CustomPublicKeyProposition parse(Reader reader) {
        return null;
    }
}
