package com.horizen.evm.interop;

public enum NativeTracers {

    CALL_TRACER("callTracer"),
    FOUR_BYTE_TRACER("4byteTracer")
    ;

    private final String text;

    NativeTracers(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

}
