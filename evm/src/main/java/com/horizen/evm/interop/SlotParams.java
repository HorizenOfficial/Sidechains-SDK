package com.horizen.evm.interop;

import com.horizen.evm.utils.Hash;

public class SlotParams extends AccountParams {
    public Hash slot;

    public SlotParams(int handle, byte[] address, byte[] slot) {
        super(handle, address);
        this.slot = Hash.fromBytes(slot);
    }
}
