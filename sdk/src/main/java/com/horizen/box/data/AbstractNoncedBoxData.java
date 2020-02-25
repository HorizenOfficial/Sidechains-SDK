package com.horizen.box.data;

import com.horizen.box.AbstractNoncedBox;
import com.horizen.proposition.Proposition;
import com.horizen.utils.Utils;

import java.util.Arrays;
import java.util.Objects;

public abstract class AbstractNoncedBoxData<P extends Proposition, B extends AbstractNoncedBox<P, BD, B>, BD extends AbstractNoncedBoxData<P, B, BD>>
        implements NoncedBoxData<P, B> {

    private final P proposition;
    private final long value;

    public AbstractNoncedBoxData(P proposition, long value) {
        Objects.requireNonNull(proposition, "proposition must be defined");

        this.proposition = proposition;
        this.value = value;
    }

    @Override
    public final long value() {
        return value;
    }

    @Override
    public final P proposition() {
        return proposition;
    }

    @Override
    public abstract byte[] bytes();

    @Override
    public byte[] customFieldsHash() {
        // By default no custom fields present, so return all zeros hash.
        return Utils.ZEROS_HASH;
    }

    @Override
    public int hashCode() {
        return Objects.hash(proposition(), value(), Arrays.hashCode(customFieldsHash()));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (!(this.getClass().equals(obj.getClass())))
            return false;
        AbstractNoncedBoxData boxData = (AbstractNoncedBoxData) obj;
        return proposition().equals(boxData.proposition())
                && value() == boxData.value()
                && Arrays.equals(customFieldsHash(), boxData.customFieldsHash());
    }
}
