package io.horizen.evm;

public class MemoryDatabase extends Database {
    /**
     * Open an ephemeral key-value database in memory.
     */
    public MemoryDatabase() {
        super(LibEvm.invoke("OpenMemoryDB", int.class));
    }

    @Override
    public String toString() {
        return String.format("MemoryDatabase{handle=%d}", handle);
    }
}
