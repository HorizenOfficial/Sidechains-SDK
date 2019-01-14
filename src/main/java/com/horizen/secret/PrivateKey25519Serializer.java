package com.horizen.secret;

import com.google.common.primitives.Bytes;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.crypto.signatures.Curve25519;

import java.util.Arrays;

public class PrivateKey25519Serializer implements SecretSerializer<PrivateKey25519>
{
    @Override
    public byte[] toBytes(PrivateKey25519 secret) {
        return Bytes.concat(secret.privateKeyBytes(), secret.publicKeyBytes());
    }

    @Override
    public Try<PrivateKey25519> parseBytes(byte[] bytes) {
        try {
            byte[] privateKeyBytes = Arrays.copyOf(bytes, Curve25519.KeyLength());
            byte[] publicKeyBytes = Arrays.copyOfRange(bytes, Curve25519.KeyLength(), Curve25519.KeyLength()+ Curve25519.KeyLength());
            PrivateKey25519 secret = new PrivateKey25519(privateKeyBytes, publicKeyBytes);
            return new Success<PrivateKey25519>(secret);
        } catch (Exception e) {
            return new Failure(e);
        }
    }
}
