package com.horizen.evm;

public class MemoryDatabase extends Database {
    public MemoryDatabase() {
        super(LibEvm.openMemoryDB());
    }
}
