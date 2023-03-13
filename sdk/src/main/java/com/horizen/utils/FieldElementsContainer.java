package com.horizen.utils;

import com.horizen.librustsidechains.FieldElement;

import java.util.Collection;

public final class FieldElementsContainer implements AutoCloseable {
    private final Collection<FieldElement> fieldElementCollection;

    public FieldElementsContainer(Collection<FieldElement> fieldElementCollection) {
        this.fieldElementCollection = fieldElementCollection;
    }

    public Collection<FieldElement> getFieldElementCollection() {
        return fieldElementCollection;
    }

    public int size() {
        return fieldElementCollection.size();
    }

    @Override
    public void close() throws Exception {
        for (FieldElement fe : fieldElementCollection) {
            fe.close();
        }
    }
}
