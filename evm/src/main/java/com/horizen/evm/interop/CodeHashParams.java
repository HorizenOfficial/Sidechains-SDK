package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

public class CodeHashParams extends AccountParams {
    public Hash codeHash;

    public CodeHashParams(int handle, Address address, byte[] codeHash) {
        super(handle, address);
        this.codeHash = Hash.fromBytes(codeHash);
    }
}
