package io.horizen.secret;

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
        byte[] secretKey = reader.getBytes(VrfSecretKey.SECRET_KEY_LENGTH);
        byte[] publicKey = reader.getBytes(VrfSecretKey.PUBLIC_KEY_LENGTH);

        VrfSecretKey vrfSecretKey = new VrfSecretKey(secretKey, publicKey);

        // Considering that isPublicKeyValid() is time-consuming operation and public key may not be valid only when
        // it was red from somewhere(in all cases it's generated from private key), key validation was put here.
        if(!vrfSecretKey.isPublicKeyValid()) {
            throw new IllegalArgumentException("The public key is not corresponds to the secret key.");
        }

        return vrfSecretKey;
    }
}
