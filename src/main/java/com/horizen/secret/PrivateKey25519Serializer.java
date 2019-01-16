package com.horizen.secret;

import com.google.common.primitives.Bytes;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.crypto.signatures.Curve25519;

import java.util.Arrays;

public class PrivateKey25519Serializer implements SecretSerializer<PrivateKey25519>
{
    private static PrivateKey25519Serializer serializer;

    static {
        serializer = new PrivateKey25519Serializer();
    }

    private PrivateKey25519Serializer() {
        super();

    }

    public static PrivateKey25519Serializer getSerializer() {
        return serializer;
    }

    @Override
    public byte[] toBytes(PrivateKey25519 secret) {
        return secret.bytes();
    }

    @Override
    public Try<PrivateKey25519> parseBytes(byte[] bytes) {
        return PrivateKey25519.parseBytes(bytes);
    }
}
