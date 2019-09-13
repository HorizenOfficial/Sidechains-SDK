package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.proposition.PublicKey25519Proposition;
import io.circe.Json;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.core.utils.ScorexEncoder;

import java.util.Arrays;

public final class RegularBox
    extends PublicKey25519NoncedBox<PublicKey25519Proposition>
    implements CoinsBox<PublicKey25519Proposition>
{

    public static final byte BOX_TYPE_ID = 1;

    public RegularBox(PublicKey25519Proposition proposition,
               long nonce,
               long value)
    {
        super(proposition, nonce, value);
    }

    @Override
    public byte boxTypeId() {
        return BOX_TYPE_ID;
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(_proposition.bytes(), Longs.toByteArray(_nonce), Longs.toByteArray(_value));
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public BoxSerializer serializer() {
        return RegularBoxSerializer.getSerializer();
    }

    public static RegularBox parseBytes(byte[] bytes) {
        PublicKey25519Proposition t = PublicKey25519Proposition.parseBytes(Arrays.copyOf(bytes, PublicKey25519Proposition.getLength()));
        long nonce = Longs.fromByteArray(Arrays.copyOfRange(bytes, PublicKey25519Proposition.getLength(), PublicKey25519Proposition.getLength() + 8));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, PublicKey25519Proposition.getLength() + 8, PublicKey25519Proposition.getLength() + 16));
        return new RegularBox(t, nonce, value);
    }

    @Override
    public Json toJson() {
        scala.collection.mutable.HashMap<String,Json> values = new scala.collection.mutable.HashMap<>();
        ScorexEncoder encoder = new ScorexEncoder();

        values.put("id", Json.fromString(encoder.encode(this.id())));
        values.put("proposition", this._proposition.toJson());
        values.put("value", Json.fromLong(this._value));
        values.put("nonce", Json.fromLong(this._nonce));
        values.put("typeId", Json.fromLong(this.boxTypeId()));

        return Json.obj(values.toSeq());
    }

    public static RegularBox parseJson(Json json) {
        return null;
    }
}
