package com.horizen.secret;

import com.horizen.utils.Checker;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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
        byte[] secretKey = Checker.readBytes(reader, VrfSecretKey.SECRET_KEY_LENGTH, "secret key");
        byte[] publicKey = Checker.readBytes(reader, VrfSecretKey.PUBLIC_KEY_LENGTH, "public key");

        return new VrfSecretKey(secretKey, publicKey);
    }
}
