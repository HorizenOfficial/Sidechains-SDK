package com.horizen.evm;

import com.horizen.evm.library.Evm;

public class StateDB implements AutoCloseable {
    private final int handle;

    public StateDB(String stateRootHex) throws Exception {
        handle = check(Evm.Instance.OpenState(stateRootHex)).handle;
    }

    public String GetIntermediateStateRoot() throws Exception {
        return check(Evm.Instance.GetIntermediateStateRoot(handle)).stateRoot;
    }

    @Override
    public void close() throws Exception {
        check(Evm.Instance.CloseState(handle));
    }

    @Override
    public String toString() {
        return String.format("StateDB{handle=%d}", handle);
    }

    private <T extends Evm.InteropResult> T check(T result) throws Exception {
        if (result.code != 0) {
            throw new Exception(result.message);
        }
        return result;
    }
}
