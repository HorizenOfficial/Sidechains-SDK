package io.horizen.evm.params;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.horizen.evm.results.EvmLog;

public class AddLogParams extends HandleParams {
    @JsonUnwrapped
    public final EvmLog log;

    public AddLogParams(int handle, EvmLog log) {
        super(handle);
        this.log = log;
    }
}
