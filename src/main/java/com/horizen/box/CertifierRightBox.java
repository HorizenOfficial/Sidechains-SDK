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

    public static final byte BOX_TYPE_ID = 2;
    // CertifierRightBox can be opened starting from specified Withdrawal epoch.
    private long _activeFromWithdrawalEpoch;

    // CertifierLock coins are not transmitted to SC, so CertifierRightBox is not a CoinsBox
    public CertifierRightBox(PublicKey25519Proposition proposition,
                             long nonce,
                             long activeFromWithdrawalEpoch)
    {
        super(proposition, nonce, 0);
        _activeFromWithdrawalEpoch = activeFromWithdrawalEpoch;
    }

    public long activeFromWithdrawalEpoch() {
        return _activeFromWithdrawalEpoch;
    }

    @Override
    public byte boxTypeId() {
        return BOX_TYPE_ID;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(this.getClass().equals(obj.getClass())))
            return false;
        if (obj == this)
            return true;
        return Arrays.equals(id(), ((CertifierRightBox) obj).id())
                && value() == ((CertifierRightBox) obj).value()
                && activeFromWithdrawalEpoch() == ((CertifierRightBox) obj).activeFromWithdrawalEpoch();
    }

    @Override
    public String toString() {
        return String.format("%s(id: %s, proposition: %s, nonce: %d, epoch: %d)", this.getClass().toString(), encoder().encode(id()), _proposition, _nonce, _activeFromWithdrawalEpoch);
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(_proposition.bytes(), Longs.toByteArray(_nonce), Longs.toByteArray(_activeFromWithdrawalEpoch));
    }

    @Override
    public BoxSerializer serializer() {
        return CertifierRightBoxSerializer.getSerializer();
    }

    public static Try<CertifierRightBox> parseBytes(byte[] bytes) {
        try {
            Try<PublicKey25519Proposition> t = PublicKey25519Proposition.parseBytes(Arrays.copyOf(bytes, PublicKey25519Proposition.getLength()));
            long nonce = Longs.fromByteArray(Arrays.copyOfRange(bytes, PublicKey25519Proposition.getLength(), PublicKey25519Proposition.getLength() + 8));
            long minimumWithdrawalEpoch = Longs.fromByteArray(Arrays.copyOfRange(bytes, PublicKey25519Proposition.getLength() + 8, PublicKey25519Proposition.getLength() + 16));
            CertifierRightBox box = new CertifierRightBox(t.get(), nonce, minimumWithdrawalEpoch);
            return new Success<>(box);
        } catch (Exception e) {
            return new Failure<>(e);
        }
    }
}
