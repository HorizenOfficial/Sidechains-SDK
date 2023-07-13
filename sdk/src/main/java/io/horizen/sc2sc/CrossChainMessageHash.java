package io.horizen.sc2sc;

import io.horizen.utils.BytesUtils;
import sparkz.core.serialization.BytesSerializable;

public final class CrossChainMessageHash implements BytesSerializable {
    private static final int HASH_SIZE = 32;
    private final byte[] val;

    public CrossChainMessageHash(byte[] val){
        if (val.length != HASH_SIZE) {
            throw new IllegalArgumentException("The CrossChain message hash must be 32 bytes long");
        }
        this.val = val;
    }
    public byte[] getValue(){
        return this.val;
    }

    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    public CrossChainMessageHashSerializer serializer() {
        return CrossChainMessageHashSerializer.getSerializer();
    }

    @Override
    public String toString() {
        return "CrossChainMessageHash{" +
                    BytesUtils.toHexString(val) +
                '}';
    }
}

