package com.horizen.customtypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.Box;
import com.horizen.box.BoxSerializer;
import com.horizen.serialization.Views;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.crypto.hash.Blake2b256;

import java.util.Arrays;
import java.util.Objects;

@JsonView(Views.Default.class)
public class CustomBox implements Box<CustomPublicKeyProposition>
{
    public static final byte BOX_TYPE_ID = 1;

    CustomPublicKeyProposition _proposition;

    @JsonProperty("value")
    long _value;

    public CustomBox (CustomPublicKeyProposition proposition, long value) {
        _proposition = proposition;
        _value = value;
    }

    @Override
    public long value() {
        return _value;
    }

    @Override
    public CustomPublicKeyProposition proposition() {
        return _proposition;
    }

    @JsonProperty("id")
    //TODO
    @Override
    public byte[] id() {
        return Blake2b256.hash(Bytes.concat(_proposition.bytes(), Longs.toByteArray(_value)));
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(_proposition.bytes(), Longs.toByteArray(_value));
    }

    @Override
    public BoxSerializer serializer() {
        return CustomBoxSerializer.getSerializer();
    }

    @Override
    public byte boxTypeId() {
        return BOX_TYPE_ID;
    }

    public static Try<CustomBox> parseBytes(byte[] bytes) {
        try {
            Try<CustomPublicKeyProposition> t = CustomPublicKeyProposition.parseBytes(Arrays.copyOf(bytes, CustomPublicKeyProposition.getLength()));
            long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, CustomPublicKeyProposition.getLength(), CustomPublicKeyProposition.getLength() + 8));
            CustomBox box = new CustomBox(t.get(), value);
            return new Success<>(box);
        } catch (Exception e) {
            return new Failure<>(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomBox customBox = (CustomBox) o;
        return _value == customBox._value &&
                Objects.equals(_proposition, customBox._proposition)
        ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_proposition, _value);
    }

    @Override
    public String toString() {
        return "CustomBox{" +
                "_proposition=" + _proposition +
                ", _value=" + _value +
                '}';
    }

}
