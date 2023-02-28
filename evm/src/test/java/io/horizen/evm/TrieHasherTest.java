package io.horizen.evm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TrieHasherTest extends LibEvmTestBase {
    @Test
    public void trieHasher() {
        // verify that:
        // - interop works
        // - we get the well-known empty root hash for anything that results in an empty trie
        // - we get something different from the empty root hash for non-empty lists
        // - same root hashes for same inputs
        // - different root hashes for different inputs
        var hashEmptyTest = TrieHasher.Root(null);
        var hashEmptyTest2 = TrieHasher.Root(new byte[0][0]);
        var hashA = TrieHasher.Root(new byte[][] {{1}, {2}, {3}});
        var hashA2 = TrieHasher.Root(new byte[][] {{1}, {2}, {3}});
        var hashB = TrieHasher.Root(new byte[][] {{1, 2, 3}});
        var hashC = TrieHasher.Root(new byte[][] {{1, 2}, {3, 4}, {1}});
        var hashD = TrieHasher.Root(new byte[][] {{1, 2}, {3, 4}, {2}});
        var hashE = TrieHasher.Root(new byte[][] {{-127, 127}, {3, 4}, {0, 0}});
        var hashF = TrieHasher.Root(new byte[200][1]);
        var hashG = TrieHasher.Root(new byte[1000][67]);

        assertEquals("should return well-known empty root hash", StateDB.EMPTY_ROOT_HASH, hashEmptyTest);
        assertEquals("should return well-known empty root hash", hashEmptyTest, hashEmptyTest2);
        assertNotEquals("should not give empty root hash", hashEmptyTest, hashA);
        assertEquals("should return same root hash for same input", hashA, hashA2);
        assertNotEquals("should return different root hash for different input", hashA, hashB);
        assertNotEquals("should return different root hash for different input", hashB, hashC);
        assertNotEquals("should return different root hash for different input", hashC, hashD);
        assertNotEquals("should return different root hash for different input", hashD, hashE);
        assertNotEquals("should return different root hash for different input", hashE, hashF);
        assertNotEquals("should return different root hash for different input", hashF, hashG);

        // TODO: generate some receipts, RLP encode them and verify the root hash
    }
}
