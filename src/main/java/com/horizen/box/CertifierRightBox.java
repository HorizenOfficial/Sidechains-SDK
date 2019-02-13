package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.proposition.PublicKey25519Proposition;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import java.util.Arrays;


public final class CertifierRightBox extends PublicKey25519NoncedBox<PublicKey25519Proposition>
{
    // CertifierLock coins are not transmitted to SC, so CertifierRightBox is not a CoinsBox
    public CertifierRightBox(PublicKey25519Proposition proposition,
                             long nonce)
    {
        super(proposition, nonce, 0);
    }

    @Override
    public byte boxTypeId() {
        return 1;
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(_proposition.bytes(), Longs.toByteArray(_nonce));
    }

    @Override
    public BoxSerializer serializer() {
        return CertifierRightBoxSerializer.getSerializer();
    }

    public static Try<CertifierRightBox> parseBytes(byte[] bytes) {
        try {
            Try<PublicKey25519Proposition> t = PublicKey25519Proposition.parseBytes(Arrays.copyOf(bytes, PublicKey25519Proposition.getLength()));
            long nonce = Longs.fromByteArray(Arrays.copyOfRange(bytes, PublicKey25519Proposition.getLength(), PublicKey25519Proposition.getLength() + 8));
            CertifierRightBox box = new CertifierRightBox(t.get(), nonce);
            return new Success<>(box);
        } catch (Exception e) {
            return new Failure<>(e);
        }
    }
}
