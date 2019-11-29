package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.proposition.MCPublicKeyHashProposition;
import com.horizen.utils.BytesUtils;
import scorex.crypto.hash.Blake2b256;

import java.util.Arrays;

public final class WithdrawalRequestBox
    implements NoncedBox<MCPublicKeyHashProposition>,
    CoinsBox<MCPublicKeyHashProposition>
{

    public static final byte BOX_TYPE_ID = 2;

    protected MCPublicKeyHashProposition proposition;
    protected long nonce;
    protected long value;

    public WithdrawalRequestBox(MCPublicKeyHashProposition proposition,
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
    public MCPublicKeyHashProposition proposition() {
        return this.proposition;
    }

    @Override
    public long nonce() {
        return this.nonce;
    }

    @Override
    public long value() {
        return this.value;
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
        MCPublicKeyHashProposition t = MCPublicKeyHashProposition.parseBytes(Arrays.copyOf(bytes, MCPublicKeyHashProposition.getLength()));
        long nonce = Longs.fromByteArray(Arrays.copyOfRange(bytes, MCPublicKeyHashProposition.getLength(), MCPublicKeyHashProposition.getLength() + 8));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, MCPublicKeyHashProposition.getLength() + 8, MCPublicKeyHashProposition.getLength() + 16));
        return new WithdrawalRequestBox(t, nonce, value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.proposition.bytes());
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

    @Override
    public String toString() {
        return String.format("%s(id: %s, proposition: %s, nonce: %d, value: %d)", this.getClass().toString(), BytesUtils.toHexString(id()), proposition, nonce, value);
    }

}
