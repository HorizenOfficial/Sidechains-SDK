package com.horizen.evm;

import com.horizen.evm.library.LibEvm;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class StateDBTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void TestStateDB() throws Exception {
        var databaseFolder = tempFolder.newFolder("evm-db");

        String hashNull = "0x0000000000000000000000000000000000000000000000000000000000000000";
        String hashEmpty = "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421";
        String origin = "0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b";

        LibEvm.Initialize(databaseFolder.getAbsolutePath());

        String rootWithBalance1234;
        String rootWithBalance802;
        try (var statedb = new StateDB(hashNull)) {
            var intermediateRoot = statedb.GetIntermediateRoot();
            assertEquals(
                "empty state should give the hash of an empty string as the root hash",
                intermediateRoot,
                hashEmpty
            );

            var committedRoot = statedb.Commit();
            assertEquals("committed root should equal intermediate root", committedRoot, intermediateRoot);
            assertEquals(statedb.GetBalance(origin), "0");

            statedb.AddBalance(origin, "1234");
            assertEquals(statedb.GetBalance(origin), "1234");
            assertNotEquals(
                "intermediate root should not equal committed root anymore",
                statedb.GetIntermediateRoot(),
                committedRoot
            );
            rootWithBalance1234 = statedb.Commit();

            statedb.SubBalance(origin, "432");
            assertEquals(statedb.GetBalance(origin), "802");
            rootWithBalance802 = statedb.Commit();

            assertEquals(statedb.GetNonce(origin), 0);
            statedb.SetNonce(origin, 3);
            assertEquals(statedb.GetNonce(origin), 3);
        }
        // verify that automatic resource management worked and StateDB.close() was called,
        // if it did the handle is invalid now and this should throw
        assertThrows(Exception.class, () -> LibEvm.StateIntermediateRoot(1));

        try (var statedb = new StateDB(rootWithBalance1234)) {
            assertEquals(statedb.GetBalance(origin), "1234");
        }

        try (var statedb = new StateDB(rootWithBalance802)) {
            assertEquals(statedb.GetBalance(origin), "802");
        }
    }
}
