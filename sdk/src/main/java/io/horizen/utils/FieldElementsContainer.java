package io.horizen.utils;

import com.horizen.librustsidechains.FieldElement;

import java.util.Collection;
import java.util.List;

public final class FieldElementsContainer implements AutoCloseable {
    private final List<FieldElement> fieldElementCollection;

    public FieldElementsContainer(List<FieldElement> fieldElementCollection) {
        this.fieldElementCollection = fieldElementCollection;
    }

    public List<FieldElement> getFieldElementCollection() {
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
