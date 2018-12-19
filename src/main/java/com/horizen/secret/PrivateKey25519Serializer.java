package com.horizen.secret;

import scala.util.Try;

public class PrivateKey25519Serializer<S extends PrivateKey25519> implements SecretSerializer<S>
{
    @Override
    public byte[] toBytes(S obj) {
        return new byte[0];
    }

    @Override
    public Try<S> parseBytes(byte[] bytes) {
        return null;
    }
}
