package com.horizen.proof;

import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.serialization.JsonSerializable;
import com.horizen.serialization.JsonSerializer;
import io.circe.Json;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.core.utils.ScorexEncoder;
import scorex.crypto.signatures.Curve25519;

import java.util.Arrays;
import java.util.Objects;

public final class Signature25519
    implements ProofOfKnowledge<PrivateKey25519, PublicKey25519Proposition>
    , JsonSerializable
{

    public static int SIGNATURE_LENGTH = Curve25519.SignatureLength();
    byte[] _signatureBytes;

    public Signature25519 (byte[] signatureBytes) {
        if(signatureBytes.length != SIGNATURE_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect signature length, %d expected, %d found", SIGNATURE_LENGTH,
                    signatureBytes.length));

        _signatureBytes = Arrays.copyOf(signatureBytes, SIGNATURE_LENGTH);
    }

    @Override
    public boolean isValid(PublicKey25519Proposition proposition, byte[] message) {
        return Curve25519.verify(_signatureBytes, message, proposition.pubKeyBytes());
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(_signatureBytes, SIGNATURE_LENGTH);
    }

    @Override
    public ProofSerializer serializer() {
        return Signature25519Serializer.getSerializer();
    }

    public static Signature25519 parseBytes(byte[] bytes) {
        return new Signature25519(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Signature25519 that = (Signature25519) o;
        return SIGNATURE_LENGTH == that.SIGNATURE_LENGTH &&
                Arrays.equals(_signatureBytes, that._signatureBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(SIGNATURE_LENGTH);
        result = 31 * result + Arrays.hashCode(_signatureBytes);
        return result;
    }

    @Override
    public Json toJson() {
        scala.collection.mutable.HashMap<String,Json> values = new scala.collection.mutable.HashMap<>();
        ScorexEncoder encoder = new ScorexEncoder();

        values.put("signature", Json.fromString(encoder.encode(this._signatureBytes)));

        return Json.obj(values.toSeq());
    }

    public static Signature25519 parseJson(Json json) {
        return null;
    }

}
