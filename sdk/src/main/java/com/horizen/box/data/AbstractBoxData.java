package com.horizen.box.data;

import com.horizen.box.AbstractNoncedBox;
import com.horizen.proposition.Proposition;

import java.util.Arrays;
import java.util.Objects;

public abstract class AbstractBoxData<P extends Proposition> implements BoxData<P> {

    private P proposition;
    private long value;

    public AbstractBoxData(P proposition, long value) {
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
    public int hashCode() {
        return proposition().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(this.getClass().equals(obj.getClass())))
            return false;
        if (obj == this)
            return true;
        return proposition() == ((AbstractBoxData) obj).proposition()
                && value() == ((AbstractBoxData) obj).value();
    }
}
