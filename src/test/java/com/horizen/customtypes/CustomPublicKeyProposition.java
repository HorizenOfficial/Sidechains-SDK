package com.horizen.customtypes;

import com.horizen.ScorexEncoding;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PropositionSerializer;
import com.horizen.utils.BytesUtils;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import java.util.Arrays;

public class CustomPublicKeyProposition extends ScorexEncoding implements ProofOfKnowledgeProposition<CustomPrivateKey>
{

    private static final int KEY_LENGTH = 128;
    private byte[] _pubKeyBytes;

    public CustomPublicKeyProposition (byte[] pubKeyBytes) {
        if(pubKeyBytes.length != KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect pubKey length, %d expected, %d found", KEY_LENGTH, pubKeyBytes.length));

        _pubKeyBytes = Arrays.copyOf(pubKeyBytes, KEY_LENGTH);
    }

    @Override
    public byte[] bytes() {
        return _pubKeyBytes;
    }

    @Override
    public PropositionSerializer serializer() {
        return CustomPublicKeyPropositionSerializer.getSerializer();
    }

    public static Try<CustomPublicKeyProposition> parseBytes(byte[] bytes) {
        try {
            CustomPublicKeyProposition proposition = new CustomPublicKeyProposition(bytes);
            return new Success<CustomPublicKeyProposition>(proposition);
        } catch (Exception e) {
            return new Failure(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomPublicKeyProposition that = (CustomPublicKeyProposition) o;
        return Arrays.equals(_pubKeyBytes, that._pubKeyBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_pubKeyBytes);
    }

    public static int getLength() {
        return KEY_LENGTH;
    }

    @Override
    public String toString() {
        return "CustomPublicKeyProposition{" +
                "_pubKeyBytes=" + BytesUtils.toHexString(_pubKeyBytes) +
                '}';
    }

/*    @Override
    public Json toJson() {
        return null;
    }

    @Override
    public JsonSerializer<JsonSerializable> jsonSerializer() {
        return null;
    }*/
}
