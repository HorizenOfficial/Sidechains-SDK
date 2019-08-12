package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.serialization.JsonSerializable;
import com.horizen.serialization.JsonSerializer;
import io.circe.Json;
import io.circe.Json$;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.core.utils.ScorexEncoder;

import java.util.Arrays;

public final class RegularBox
    extends PublicKey25519NoncedBox<PublicKey25519Proposition>
    implements CoinsBox<PublicKey25519Proposition>
    , JsonSerializable
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

    public static Try<RegularBox> parseBytes(byte[] bytes) {
        try {
            Try<PublicKey25519Proposition> t = PublicKey25519Proposition.parseBytes(Arrays.copyOf(bytes, PublicKey25519Proposition.getLength()));
            long nonce = Longs.fromByteArray(Arrays.copyOfRange(bytes, PublicKey25519Proposition.getLength(), PublicKey25519Proposition.getLength() + 8));
            long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, PublicKey25519Proposition.getLength() + 8, PublicKey25519Proposition.getLength() + 16));
            RegularBox box = new RegularBox(t.get(), nonce, value);
            return new Success<>(box);
        } catch (Exception e) {
            return new Failure<>(e);
        }
    }

    @Override
    public Json toJson() {
        scala.collection.mutable.HashMap<String,Json> values = new scala.collection.mutable.HashMap<>();
        ScorexEncoder encoder = new ScorexEncoder();

        values.put("id", Json.fromString(encoder.encode(this.id())));
        values.put("proposition", this._proposition.toJson());
        values.put("value", Json.fromLong(this._value));
        values.put("nonce", Json.fromLong(this._nonce));

        return Json.obj(values.toSeq());
    }

    @Override
    public JsonSerializer<JsonSerializable> jsonSerializer() {
        return null;
    }
}
