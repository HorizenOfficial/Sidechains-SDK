package io.horizen.cryptolibprovider.utils;

import com.horizen.librustsidechains.FieldElement;
import com.horizen.poseidonnative.PoseidonHash;

import java.util.Collection;

public final class HashUtils {
    public static FieldElement fieldElementsListHash(Collection<FieldElement> fieldElements) {
        int elementsToHash = fieldElements.size();
        try (PoseidonHash hash = PoseidonHash.getInstanceConstantLength(elementsToHash)) {
            for (FieldElement currElement : fieldElements) {
                hash.update(currElement);
            }

            return hash.finalizeHash();
        }
    }
}
