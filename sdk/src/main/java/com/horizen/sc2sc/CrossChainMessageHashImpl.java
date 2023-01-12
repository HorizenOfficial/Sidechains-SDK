package com.horizen.sc2sc;

import sparkz.core.serialization.BytesSerializable;
import sparkz.core.serialization.SparkzSerializer;

import java.util.Arrays;

public class CrossChainMessageHashImpl implements CrossChainMessageHash {

    private byte[] val;

    public CrossChainMessageHashImpl(byte[] val){
        this.val = val;
    }

    @Override
    public byte[] getValue(){
        return this.val;
    }

    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public CrossChainMessageHashSerializer serializer() {
        return CrossChainMessageHashSerializer.getSerializer();
    }

    @Override
    public String toString() {
        return "CrossChainMessageHash{" +
                 Arrays.toString(val) +
                '}';
    }
}
