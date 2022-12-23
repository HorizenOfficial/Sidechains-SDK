package com.horizen.sc2sc;

public enum CrossChainProtocolVersion {

    VERSION_1((short) 1);

    private short val;

    public static CrossChainProtocolVersion fromShort(short x) {
        switch(x) {
            case 1:
                return VERSION_1;
        }
        throw new UnsupportedOperationException("Unknown crosschain protocol version: "+x);
    }

    CrossChainProtocolVersion(short val){
        this.val = val;
    }

    public short getVal() {
        return val;
    }
}
