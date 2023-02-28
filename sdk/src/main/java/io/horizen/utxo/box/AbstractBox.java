package io.horizen.utxo.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.utxo.box.data.AbstractBoxData;
import com.horizen.proposition.Proposition;
import sparkz.crypto.hash.Blake2b256;
import sparkz.util.SparkzEncoder;

import java.util.Arrays;
import java.util.Objects;

public abstract class AbstractBox<P extends Proposition, BD extends AbstractBoxData<P, B, BD>, B extends AbstractBox<P, BD, B>>
        implements Box<P> {
    protected final BD boxData;
    protected final long nonce;

    private byte[] id;
    private Integer hashcode;

    private final static byte[] coinsBoxFlag = { (byte)1 };
    private final static byte[] nonCoinsBoxFlag = { (byte)0 };

    public AbstractBox(BD boxData, long nonce) {
        Objects.requireNonNull(boxData, "boxData must be defined");

        this.boxData = boxData;
        this.nonce = nonce;
    }

    @Override
    public final long value() {
        return boxData.value();
    }

    @Override
    public final P proposition() { return boxData.proposition(); }

    @Override
    public final long nonce() { return nonce; }

    @Override
    public final byte[] customFieldsHash() {
        return boxData.customFieldsHash();
    }

    @Override
    public final byte[] id() {
        if(id == null) {
            id = Blake2b256.hash(Bytes.concat(
                    this instanceof CoinsBox ? coinsBoxFlag : nonCoinsBoxFlag,
                    Longs.toByteArray(value()),
                    proposition().bytes(),
                    Longs.toByteArray(nonce()),
                    customFieldsHash()));
        }
        return id;
    }

    @Override
    public int hashCode() {
        if(hashcode == null)
            hashcode = Objects.hash(boxData, nonce);
        return hashcode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (!(this.getClass().equals(obj.getClass())))
            return false;
        return Arrays.equals(id(), ((AbstractBox) obj).id());
    }

    @Override
    public String toString() {
        return String.format("%s(id: %s, proposition: %s, value: %d, nonce: %d)", this.getClass().getSimpleName(), encoder().encode(id()), proposition(), value(), nonce());
    }

    /**
     * This method is only needed cause we mix java and scala types.
     * Java classes cannot use scala traits with implicit values, they treat them like unimplemented.
     * Otherwise, we can just use sparkz.util.SparkzEncoding like in other places.
     */
    public static SparkzEncoder encoder() {
        return new SparkzEncoder();
    }

    @Override
    public String typeName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public Boolean isCustom() { return true; } // All boxes presume customs until it not defined otherwise
}


