package com.horizen.validation.crosschain;

import com.horizen.block.SidechainBlockBase;

public interface CrossChainValidator<PMOD extends SidechainBlockBase<?, ?>> {
    void validate(PMOD objectToValidate) throws IllegalArgumentException;
}
