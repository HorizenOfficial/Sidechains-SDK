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
        writer.putBytes(secret.bytes());
    }

    @Override
    public VrfSecretKey parse(Reader reader) {
        return VrfSecretKey.parse(reader.getBytes(reader.remaining()));
    }
}
