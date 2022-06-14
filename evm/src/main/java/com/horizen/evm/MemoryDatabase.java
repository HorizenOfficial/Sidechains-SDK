package com.horizen.evm;

public class MemoryDatabase extends Database {
    /**
     * Open an ephemeral key-value database in memory.
     */
    public MemoryDatabase() {
        super(LibEvm.openMemoryDB());
    }

    @Override
    public String toString() {
        return String.format("MemoryDatabase{handle=%d}", handle);
    }
}
