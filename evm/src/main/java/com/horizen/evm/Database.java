package com.horizen.evm;

class Database implements AutoCloseable {
    final int handle;

    public Database(int handle) {
        this.handle = handle;
    }

    @Override
    public void close() throws Exception {
        LibEvm.closeDatabase(handle);
    }
}
