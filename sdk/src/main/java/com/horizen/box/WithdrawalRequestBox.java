package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.proposition.MCPublicKeyHash;
import com.horizen.proposition.PublicKey25519Proposition;
import scorex.crypto.hash.Blake2b256;

import java.util.Arrays;

public final class WithdrawalRequestBox
    implements NoncedBox<MCPublicKeyHash>,
    CoinsBox<MCPublicKeyHash>
{

    public static final byte BOX_TYPE_ID = 2;

    protected MCPublicKeyHash proposition;
    protected long nonce;
    protected long value;

    public WithdrawalRequestBox(MCPublicKeyHash proposition,
                                long nonce,
                                long value)
    {
        this.proposition = proposition;
        this.nonce = nonce;
        this.value = value;
    }

    @Override
    public byte boxTypeId() {
        return BOX_TYPE_ID;
    }

    @Override
    public byte[] id() {
        return Blake2b256.hash(Bytes.concat(this.proposition.bytes(), Longs.toByteArray(this.nonce)));    }

    @Override
    public MCPublicKeyHash proposition() {
        return null;
    }

    @Override
    public long nonce() {
        return 0;
    }

    @Override
    public long value() {
        return 0;
    }

    @Override
    public BoxSerializer serializer() {
        return WithdrawalRequestBoxSerializer.getSerializer();
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(proposition.bytes(), Longs.toByteArray(nonce), Longs.toByteArray(value));
    }

    public static WithdrawalRequestBox parseBytes(byte[] bytes) {
        MCPublicKeyHash t = MCPublicKeyHash.parseBytes(Arrays.copyOf(bytes, MCPublicKeyHash.getLength()));
        long nonce = Longs.fromByteArray(Arrays.copyOfRange(bytes, MCPublicKeyHash.getLength(), MCPublicKeyHash.getLength() + 8));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, MCPublicKeyHash.getLength() + 8, MCPublicKeyHash.getLength() + 16));
        return new WithdrawalRequestBox(t, nonce, value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.bytes());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(this.getClass().equals(obj.getClass())))
            return false;
        if (obj == this)
            return true;
        return Arrays.equals(id(), ((WithdrawalRequestBox) obj).id())
                && value() == ((WithdrawalRequestBox) obj).value();
    }
}
