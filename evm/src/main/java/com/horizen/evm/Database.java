package com.horizen.evm;

class Database extends ResouceHandle {
    public Database(int handle) {
        super(handle);
    }

    @Override
    public void close() throws Exception {
        LibEvm.closeDatabase(handle);
    }
}
