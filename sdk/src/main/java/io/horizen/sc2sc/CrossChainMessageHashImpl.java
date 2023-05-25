package io.horizen.sc2sc;

import io.horizen.sc2sc.CrossChainMessageHash;
import io.horizen.sc2sc.CrossChainMessageHashSerializer;

import java.util.Arrays;

public class CrossChainMessageHashImpl implements CrossChainMessageHash {

    private final byte[] val;

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
