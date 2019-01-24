package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.proposition.PublicKey25519Proposition;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import java.util.Arrays;

public final class RegularBoxSerializer implements BoxSerializer<RegularBox>
{

    private static RegularBoxSerializer serializer;

    static {
        serializer = new RegularBoxSerializer();
    }

    private RegularBoxSerializer() {
        super();

    }

    public static RegularBoxSerializer getSerializer() {
        return serializer;
    }

    @Override
    public byte[] toBytes(RegularBox box) {
        return box.bytes();
    }

    @Override
    public Try<RegularBox> parseBytes(byte[] bytes) {
        try {
            PublicKey25519Proposition proposition = new PublicKey25519Proposition(Arrays.copyOf(bytes, PublicKey25519Proposition.KEY_LENGTH));
            long nonce = Longs.fromByteArray(Arrays.copyOfRange(bytes, PublicKey25519Proposition.KEY_LENGTH, PublicKey25519Proposition.KEY_LENGTH + 8));
            long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, PublicKey25519Proposition.KEY_LENGTH + 8, PublicKey25519Proposition.KEY_LENGTH + 16));
            RegularBox box = new RegularBox(proposition, nonce, value);
            return new Success<>(box);
        }
        catch (Exception e) {
            return new Failure(e);
        }
    }
}
