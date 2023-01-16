package com.horizen.account.secret;

import com.horizen.account.utils.Secp256k1;
import com.horizen.secret.SecretSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class PrivateKeySecp256k1Serializer implements SecretSerializer<PrivateKeySecp256k1> {
    private static final PrivateKeySecp256k1Serializer serializer;

    static {
        serializer = new PrivateKeySecp256k1Serializer();
    }

    private PrivateKeySecp256k1Serializer() {
        super();
    }

    public static PrivateKeySecp256k1Serializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(PrivateKeySecp256k1 secret, Writer writer) {
        writer.putBytes(secret.privateKeyBytes());
    }

    @Override
    public PrivateKeySecp256k1 parse(Reader reader) {
        byte[] privateKey = reader.getBytes(Secp256k1.PRIVATE_KEY_SIZE);
        return new PrivateKeySecp256k1(privateKey);
    }
}
