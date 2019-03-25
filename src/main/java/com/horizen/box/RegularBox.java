package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.proposition.PublicKey25519Proposition;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import java.util.Arrays;

public final class RegularBox extends PublicKey25519NoncedBox<PublicKey25519Proposition> implements CoinsBox<PublicKey25519Proposition>
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
}
