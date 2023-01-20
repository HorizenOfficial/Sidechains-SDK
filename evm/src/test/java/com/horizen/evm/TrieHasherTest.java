package com.horizen.evm;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

public class TrieHasherTest extends LibEvmTestBase {
    private void assertArrayNotEquals(String message, byte[] unexpected, byte[] actual) {
        assertFalse(message, Arrays.equals(unexpected, actual));
    }

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

        assertArrayEquals("should return well-known empty root hash", hashEmpty, hashEmptyTest);
        assertArrayEquals("should return well-known empty root hash", hashEmptyTest, hashEmptyTest2);
        assertArrayNotEquals("should not give empty root hash", hashEmptyTest, hashA);
        assertArrayEquals("should return same root hash for same input", hashA, hashA2);
        assertArrayNotEquals("should return different root hash for different input", hashA, hashB);
        assertArrayNotEquals("should return different root hash for different input", hashB, hashC);
        assertArrayNotEquals("should return different root hash for different input", hashC, hashD);
        assertArrayNotEquals("should return different root hash for different input", hashD, hashE);
        assertArrayNotEquals("should return different root hash for different input", hashE, hashF);
        assertArrayNotEquals("should return different root hash for different input", hashF, hashG);

        // TODO: generate some receipts, RLP encode them and verify the root hash
    }
}
