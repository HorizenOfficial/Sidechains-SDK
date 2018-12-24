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

    @Override
    public long value() {
        return _value;
    }

    @Override
    public PKP proposition() { return _proposition; }

    @Override
    public long nonce() { return _nonce; }

    @Override
    public byte[] id() { // actually return ADKey
        return null;
    }

    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public PublicKey25519NoncedBoxSerializer serializer() {
        return new PublicKey25519NoncedBoxSerializer();
    }
}

class PublicKey25519NoncedBoxSerializer<PKNB extends PublicKey25519NoncedBox> implements BoxSerializer<PKNB>
{
    @Override
    public Try<PKNB> parseBytes(byte[] bytes) {
        return null;
    }

    @Override
    public byte[] toBytes(PKNB obj) {
        return null;
    }
}

