package com.horizen.evm;

public class MemoryDatabase extends Database {
    public MemoryDatabase() throws Exception {
        super(LibEvm.openMemoryDB());
    }
}
