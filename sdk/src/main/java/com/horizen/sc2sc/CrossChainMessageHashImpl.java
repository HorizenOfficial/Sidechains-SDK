package com.horizen.sc2sc;

import sparkz.core.serialization.BytesSerializable;
import sparkz.core.serialization.SparkzSerializer;

public class CrossChainMessageHashImpl implements CrossChainMessageHash {

    private byte[] val;

    public CrossChainMessageHashImpl(byte[] val){
        this.val = val;
    }


    @Override
    public byte[] bytes() {
        return val;
    }

    @Override
    public SparkzSerializer<BytesSerializable> serializer() {
        return null;
    }
}
