package com.horizen.evm;

public final class Database {
    private Database() {
    }

    public static void openMemoryDB() throws Exception {
        LibEvm.openMemoryDB();
    }

    public static void openLevelDB(String path) throws Exception {
        LibEvm.openLevelDB(path);
    }

    public static void closeDatabase() throws Exception {
        LibEvm.closeDatabase();
    }
}
