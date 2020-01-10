package com.horizen.box.data;

import com.horizen.proposition.Proposition;

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
}
