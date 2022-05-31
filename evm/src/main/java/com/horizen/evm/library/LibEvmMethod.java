package com.horizen.evm.library;

public enum LibEvmMethod {
    STRING_ONE("ONE"),
    STRING_TWO("TWO");

    private final String text;

    LibEvmMethod(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
