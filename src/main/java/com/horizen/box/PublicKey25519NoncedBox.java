package com.horizen.box;

import com.horizen.proposition.PublicKey25519Proposition;
import scala.util.Try;

public abstract class PublicKey25519NoncedBox<PKP extends PublicKey25519Proposition> implements NoncedBox<PKP>
{
    PKP _proposition;
    long _nonce;
    long _value;

    PublicKey25519NoncedBox(PKP proposition,
                            long nonce,
                            long value)
    {
        this._proposition = proposition;
        this._nonce = nonce;
        this._value = value;
    }

    public long value() {
        return _value;
    }

    public PKP proposition() { return _proposition; }

    public long nonce() { return _nonce; }

    public byte[] id() { // actually return ADKey
        return null;
    }

    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    public PublicKey25519NoncedBoxSerializer serializer() {
        return new PublicKey25519NoncedBoxSerializer();
    }
}


