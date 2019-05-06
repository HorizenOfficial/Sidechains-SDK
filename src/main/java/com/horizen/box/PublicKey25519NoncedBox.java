package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.ScorexEncoding;
import com.horizen.proposition.PublicKey25519Proposition;
import scorex.crypto.hash.Blake2b256;

import java.util.Arrays;
import java.util.Objects;

public abstract class PublicKey25519NoncedBox<PKP extends PublicKey25519Proposition> extends ScorexEncoding implements NoncedBox<PKP>
{
    protected PKP _proposition;
    protected long _nonce;
    protected long _value;

    public PublicKey25519NoncedBox(PKP proposition,
                            long nonce,
                            long value)
    {
        this._proposition = proposition;
        this._nonce = nonce;
        this._value = value;
    }

    @Override
    public final long value() {
        return _value;
    }

    @Override
    public final PKP proposition() { return _proposition; }

    @Override
    public final long nonce() { return _nonce; }

    @Override
    public byte[] id() {
        return Blake2b256.hash(Bytes.concat(_proposition.pubKeyBytes(), Longs.toByteArray(_nonce)));
    }

    @Override
    public abstract byte[] bytes();

    @Override
    public int hashCode() {
        return _proposition.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(this.getClass().equals(obj.getClass())))
            return false;
        if (obj == this)
            return true;
        return Arrays.equals(id(), ((PublicKey25519NoncedBox) obj).id())
                && value() == ((PublicKey25519NoncedBox) obj).value();
    }

    @Override
    public String toString() {
        return String.format("%s(id: %s, proposition: %s, nonce: %d, value: %d)", this.getClass().toString(), encoder().encode(id()), _proposition, _nonce, _value);
    }
}


