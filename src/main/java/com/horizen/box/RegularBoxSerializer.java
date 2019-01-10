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
    @Override
    public byte[] toBytes(RegularBox obj) {
        return Bytes.concat(obj.proposition().pubKeyBytes(), Longs.toByteArray(obj.nonce()), Longs.toByteArray(obj.value()));
    }

    @Override
    public Try<RegularBox> parseBytes(byte[] bytes) {
        try {
            PublicKey25519Proposition proposition = new PublicKey25519Proposition(Arrays.copyOf(bytes, PublicKey25519Proposition.PubKeyLength));
            long nonce = Longs.fromByteArray(Arrays.copyOfRange(bytes, PublicKey25519Proposition.PubKeyLength, PublicKey25519Proposition.PubKeyLength + 8));
            long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, PublicKey25519Proposition.PubKeyLength + 8, PublicKey25519Proposition.PubKeyLength + 16));
            RegularBox box = new RegularBox(proposition, nonce, value);
            return new Success<>(box);
        }
        catch (Exception e) {
            return new Failure(e);
        }
    }
}
