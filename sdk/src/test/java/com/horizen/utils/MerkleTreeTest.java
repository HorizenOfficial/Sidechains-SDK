package com.horizen.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MerkleTreeTest {

    // Test data receive from https://webbtc.com/block/000000000000000002775ff227222f47a46cad46b0f634db53c1a8737d7ecd41.json
    @Test
    public void MerkleTreeTest_createAndVerifyMerkleTree() {
        // set test data of 9 hashes
        ArrayList<String> transactionsHashes = new ArrayList<>();
        transactionsHashes.add("b82c849c6abcd1ad17f4457333afc45723557348d2dda6974363253223b0f378");
        transactionsHashes.add("1f4341337dde3bde02ba32919f12b0e73d668f26eaa8ca0e12ab2cff6e29a24e");
        transactionsHashes.add("974f803832125b4dcc7ee9226e57cd9c39792663fdbd34ce63f2f6d052b3cf15");
        transactionsHashes.add("2d9b08361ac900cb55c71516c513db868f38536ebac65c6de16e6db26367c970");
        transactionsHashes.add("95ca7ba2319b55818eab3f20a6cc9b973f597204fb4632c717086a10f97211dc");
        transactionsHashes.add("198dd7904fc9834464229bf4032b9487da096bb91e9c7ab0b40274fdd767015c");
        transactionsHashes.add("23257ef24076bf37fcc6116ee0e94adebea09156424c98030e061e5206ac6ab9");
        transactionsHashes.add("8e41093f3d767eae5d90ae8ddda0b5873d3587ed5be02d3875a1bee01cec1c06");
        transactionsHashes.add("8e97b4a311d2c58c019e93e9f943156fcef7dfd78973515d8f284f1b410158ba");

        // decode from hex string to bytes
        ArrayList<byte[]> hashesAsBytes = new ArrayList<>();
        for(String s : transactionsHashes)
            hashesAsBytes.add(BytesUtils.fromHexString(s));

        // set expected result data
        ArrayList<String> expectedRes = new ArrayList<>(transactionsHashes);
        expectedRes.add("308afe4e163a70120de2a641ec44342dcbd63b7a5d6789268c73899339265aee");
        expectedRes.add("ba9ca9e20a8e88ba7be1673d9af056c288e1a8a9965594697f306485b54f8639");
        expectedRes.add("199b6bb13e941afec48e4565ca980abd40e34ed3b9f10523db64fc3dce2fb7b8");
        expectedRes.add("e29c65d08f8898b646fdde3e2cc1317e2c717f8f6485af9973ef41374a51a8e3");
        expectedRes.add("9567fc20e47374adda00ee83d89ea9939798b282543ea6cbd8fb0023b57465ed");
        expectedRes.add("e7f8bb5dbb398caf50adf1667b491ed61fe4d6f1f7624508ec36cb633bf5bf82");
        expectedRes.add("61bfbdf7038dc7f21e2bcf193faef8e6caa8222af016a6ed86b9e9d860f046df");
        expectedRes.add("d257fc7591d43caac41dfc70ca8326d4a1189f8012b9ab3cd52840dbd2f94430");
        expectedRes.add("66dbf7e6a28135fd04a32bdd2d150c8585092670d48379ea8e68823f4362e2ab");
        expectedRes.add("f6374a6dd9d838b1871e0ce8d4e537011aa79c60dc12ef80a472c43bc5021aeb");
        expectedRes.add("29d000eee85f08b6482026be2d92d081d6f9418346e6b2e9fe2e9b985f24ed1e"); // merkle root


        MerkleTree merkleTree = MerkleTree.createMerkleTree(hashesAsBytes);

        // Test leaf related logic
        assertEquals("Leaves number expected to be equal", transactionsHashes.size(), merkleTree.leavesNumber());
        List<byte[]> actualLeaves = merkleTree.leaves();
        for(int i = 0; i < actualLeaves.size(); i++)
            assertEquals(String.format("Leaves expected to be equal. Leaf %d is different.", i), transactionsHashes.get(i), BytesUtils.toHexString(actualLeaves.get(i)));


        // Test that merkle tree was constructed properly
        List<byte[]> resAsBytes = merkleTree.toList();
        // Encode from bytes to hex string
        ArrayList<String> res = new ArrayList<>();
        for(byte[] bytes : resAsBytes)
            res.add(BytesUtils.toHexString(bytes));

        assertEquals("Calculated Merkle Tree size should be the same as expected Merkle Tree", expectedRes.size(), res.size());
        assertEquals("Calculated Merkle root shoud be the same as expected one", expectedRes.get(expectedRes.size() - 1), BytesUtils.toHexString(merkleTree.rootHash()));


        // Test merkle path construction:
        for(int i = 0; i < actualLeaves.size(); i++) {
            MerklePath path = merkleTree.getMerklePathForLeaf(i);
            assertEquals("Merkle path validation failed.", true, merkleTree.validateMerklePath(merkleTree.leaves().get(i), path));
        }
    }
}