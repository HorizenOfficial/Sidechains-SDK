package com.horizen.secret;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class VrfSecretKeySerializer implements SecretSerializer<VrfSecretKey> {
    private static VrfSecretKeySerializer serializer;

    static {
        serializer = new VrfSecretKeySerializer();
    }

    private VrfSecretKeySerializer() {
        super();
    }

    public static VrfSecretKeySerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(VrfSecretKey secret, Writer writer) {
        writer.putBytes(secret.secretBytes);
        writer.putBytes(secret.publicBytes);
    }

    @Override
    public VrfSecretKey parse(Reader reader) {
        byte[] secretKey = reader.getBytes(VrfSecretKey.SECRET_KEY_LENGTH);
        byte[] publicKey = reader.getBytes(VrfSecretKey.PUBLIC_KEY_LENGTH);

        return new VrfSecretKey(secretKey, publicKey);
    }
}
