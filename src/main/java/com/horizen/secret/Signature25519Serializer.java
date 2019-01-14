package com.horizen.secret;

import com.horizen.proof.ProofSerializer;

import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

public class Signature25519Serializer implements ProofSerializer<Signature25519> {

    @Override
    public byte[] toBytes(Signature25519 signature) {
        return signature.signatureBytes();
    }

    @Override
    public Try<Signature25519> parseBytes(byte[] bytes) {
        try {
            Signature25519 signature = new Signature25519(bytes);
            return new Success<Signature25519>(signature);
        } catch (Exception e) {
            return new Failure(e);
        }
    }
}
