package com.horizen.evm.interop;

public class CodeParams extends AccountParams {
    public byte[] code;

    public CodeParams(int handle, byte[] address, byte[] code) {
        super(handle, address);
        this.code = code;
    }
}
