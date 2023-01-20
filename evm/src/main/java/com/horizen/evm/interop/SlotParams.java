package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

public class SlotParams extends AccountParams {
    public Hash slot;

    public SlotParams(int handle, Address address, byte[] slot) {
        super(handle, address);
        this.slot = Hash.fromBytes(slot);
    }
}
