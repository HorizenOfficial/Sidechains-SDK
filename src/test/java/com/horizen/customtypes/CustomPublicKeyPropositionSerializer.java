package com.horizen.customtypes;

import com.horizen.proposition.PropositionSerializer;
import scala.util.Try;

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
    public Try<CustomPublicKeyProposition> parseBytes(byte[] bytes) {
        return CustomPublicKeyProposition.parseBytes(bytes);
    }
}
