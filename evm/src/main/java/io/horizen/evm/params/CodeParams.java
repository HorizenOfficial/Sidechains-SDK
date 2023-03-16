package io.horizen.evm.params;

import io.horizen.evm.Address;

public class CodeParams extends AccountParams {
    public final byte[] code;

    public CodeParams(int handle, Address address, byte[] code) {
        super(handle, address);
        this.code = code;
    }
}
