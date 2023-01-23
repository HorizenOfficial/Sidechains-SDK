package com.horizen.evm;

public abstract class Database extends ResourceHandle {
    public Database(int handle) {
        super(handle);
    }

    @Override
    public void close() throws Exception {
        LibEvm.closeDatabase(handle);
    }
}
