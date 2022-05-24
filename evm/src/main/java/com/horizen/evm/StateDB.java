package com.horizen.evm;

import com.horizen.evm.library.Evm;

public class StateDB implements AutoCloseable {
    private final int handle;

    public StateDB(String stateRootHex) throws Exception {
        var result = Evm.Instance.OpenState(stateRootHex);
        if (result.code != 0) {
            throw new Exception(result.message);
        }
        handle = result.handle;
    }

    public String GetIntermediateStateRoot() throws Exception {
        var result = Evm.Instance.GetIntermediateStateRoot(handle);
        if (result.code != 0) {
            throw new Exception(result.message);
        }
        return result.stateRoot;
    }

    @Override
    public void close() throws Exception {
        var result = Evm.Instance.CloseState(handle);
        if (result.code != 0) {
            throw new Exception(result.message);
        }
    }

    @Override
    public String toString() {
        return String.format("StateDB{handle=%d}", handle);
    }
}
