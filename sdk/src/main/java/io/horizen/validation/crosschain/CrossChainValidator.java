package io.horizen.validation.crosschain;

import io.horizen.block.SidechainBlockBase;

public interface CrossChainValidator<PMOD extends SidechainBlockBase<?, ?>> {
    void validate(PMOD objectToValidate) throws IllegalArgumentException;
}
