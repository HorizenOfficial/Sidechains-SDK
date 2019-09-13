package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.serialization.JsonSerializable;
import com.horizen.serialization.JsonSerializer;
import io.circe.Json;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.core.utils.ScorexEncoder;

import java.util.Arrays;

// CertifierLock coins are not transmitted to SC, so CertifierRightBox is not a CoinsBox
public final class CertifierRightBox
    extends PublicKey25519NoncedBox<PublicKey25519Proposition>
{

    public static final byte BOX_TYPE_ID = 2;
    // CertifierRightBox can be opened starting from specified Withdrawal epoch.
    private long _activeFromWithdrawalEpoch;

    public CertifierRightBox(PublicKey25519Proposition proposition,
                             long nonce,
                             long value,
                             long activeFromWithdrawalEpoch)
    {
        super(proposition, nonce, value);
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
        return Bytes.concat(_proposition.bytes(), Longs.toByteArray(_nonce), Longs.toByteArray(_value), Longs.toByteArray(_activeFromWithdrawalEpoch));
    }

    @Override
    public BoxSerializer serializer() {
        return CertifierRightBoxSerializer.getSerializer();
    }

    public static CertifierRightBox parseBytes(byte[] bytes) {
        PublicKey25519Proposition t = PublicKey25519Proposition.parseBytes(Arrays.copyOf(bytes, PublicKey25519Proposition.getLength()));
        long nonce = Longs.fromByteArray(Arrays.copyOfRange(bytes, PublicKey25519Proposition.getLength(), PublicKey25519Proposition.getLength() + 8));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, PublicKey25519Proposition.getLength()+ 8, PublicKey25519Proposition.getLength() + 16));
        long minimumWithdrawalEpoch = Longs.fromByteArray(Arrays.copyOfRange(bytes, PublicKey25519Proposition.getLength() + 16, PublicKey25519Proposition.getLength() + 24));
        return new CertifierRightBox(t, nonce, value, minimumWithdrawalEpoch);
    }

    @Override
    public Json toJson() {
        scala.collection.mutable.HashMap<String,Json> values = new scala.collection.mutable.HashMap<>();
        ScorexEncoder encoder = new ScorexEncoder();

        values.put("id", Json.fromString(encoder.encode(this.id())));
        values.put("proposition", this._proposition.toJson());
        values.put("value", Json.fromLong(this._value));
        values.put("nonce", Json.fromLong(this._nonce));
        values.put("activeFromWithdrawalEpoch", Json.fromLong(this._activeFromWithdrawalEpoch));
        values.put("typeId", Json.fromLong(this.boxTypeId()));

        return Json.obj(values.toSeq());
    }

    public static CertifierRightBox parseJson(Json json) {
        return null;
    }

}
