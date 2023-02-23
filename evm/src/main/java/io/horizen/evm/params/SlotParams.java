package io.horizen.evm.params;

import io.horizen.evm.Address;
import io.horizen.evm.Hash;

public class SlotParams extends AccountParams {
    public final Hash slot;

    public SlotParams(int handle, Address address, Hash slot) {
        super(handle, address);
        this.slot = slot;
    }
}
