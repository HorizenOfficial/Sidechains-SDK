package io.horizen.evm;

import io.horizen.evm.params.DatabaseParams;

public abstract class Database extends ResourceHandle {
    public Database(int handle) {
        super(handle);
    }

    @Override
    public void close() throws Exception {
        LibEvm.invoke("CloseDatabase", new DatabaseParams(handle));
    }
}
