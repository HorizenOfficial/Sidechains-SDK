package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

public class CodeParams extends AccountParams {
    public final byte[] code;

    public CodeParams(int handle, Address address, byte[] code) {
        super(handle, address);
        this.code = code;
    }
}
