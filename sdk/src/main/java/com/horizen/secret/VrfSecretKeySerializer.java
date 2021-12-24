package com.horizen.secret;

import com.horizen.cryptolibprovider.CryptoLibProvider;
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
        writer.putInt(secret.secretBytes.length);
        writer.putBytes(secret.secretBytes);
        writer.putInt(secret.publicBytes.length);
        writer.putBytes(secret.publicBytes);
    }

    @Override
    public VrfSecretKey parse(Reader reader) {
        int secretKeyLength = reader.getInt();
        byte[] secretKey = reader.getBytes(secretKeyLength);
        int publicKeyLength = reader.getInt();
        byte[] publicKey = reader.getBytes(publicKeyLength);

        return new VrfSecretKey(secretKey, publicKey);
    }
}
