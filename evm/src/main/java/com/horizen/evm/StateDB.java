package com.horizen.evm;

import com.horizen.evm.library.LibEvm;

public class StateDB implements AutoCloseable {
    private final int handle;

    public StateDB(String stateRootHex) throws Exception {
        handle = LibEvm.OpenState(stateRootHex);
    }

//    public String GetIntermediateStateRoot() throws Exception {
//        return check(Evm.Instance.GetIntermediateStateRoot(handle)).stateRoot;
//    }

    @Override
    public void close() throws Exception {
//        handle = Evm.Instance.<OpenStateParams, Evm.HandleResult>Invoke("OpenState", params).handle;
//        check(Evm.Instance.CloseState(handle));
    }

    @Override
    public String toString() {
        return String.format("StateDB{handle=%d}", handle);
    }
}
