package com.horizen.evm.params;

import com.horizen.evm.Address;
import com.horizen.evm.Hash;

public class SlotParams extends AccountParams {
    public final Hash slot;

    public SlotParams(int handle, Address address, Hash slot) {
        super(handle, address);
        this.slot = slot;
    }
}
