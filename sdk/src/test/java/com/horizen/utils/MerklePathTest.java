package com.horizen.utils;

import com.horizen.utils.Pair;
import org.junit.Test;
import scala.util.Try;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class MerklePathTest {

    // Test data receive from https://webbtc.com/block/000000000000000002775ff227222f47a46cad46b0f634db53c1a8737d7ecd41.json
    @Test
    public void MerklePathTest_createAndVerifyMerklePath() {
        // MerkleTree for 9 leaves:
        //                    19
        //               /        \
        //            17           18
        //        /       \        |
        //     14          15      16
        //    /  \        /  \     |
        //   9    10    11    12   13
        //  /\    /\    /\    /\   |
        // 0  1  2  3  4  5  6  7  8
        ArrayList<String> merkleTreeHex = new ArrayList<>();
        // leaves
        merkleTreeHex.add("b82c849c6abcd1ad17f4457333afc45723557348d2dda6974363253223b0f378"); // 0
        merkleTreeHex.add("1f4341337dde3bde02ba32919f12b0e73d668f26eaa8ca0e12ab2cff6e29a24e"); // 1
        merkleTreeHex.add("974f803832125b4dcc7ee9226e57cd9c39792663fdbd34ce63f2f6d052b3cf15"); // 2
        merkleTreeHex.add("2d9b08361ac900cb55c71516c513db868f38536ebac65c6de16e6db26367c970"); // 3
        merkleTreeHex.add("95ca7ba2319b55818eab3f20a6cc9b973f597204fb4632c717086a10f97211dc"); // 4
        merkleTreeHex.add("198dd7904fc9834464229bf4032b9487da096bb91e9c7ab0b40274fdd767015c"); // 5
        merkleTreeHex.add("23257ef24076bf37fcc6116ee0e94adebea09156424c98030e061e5206ac6ab9"); // 6
        merkleTreeHex.add("8e41093f3d767eae5d90ae8ddda0b5873d3587ed5be02d3875a1bee01cec1c06"); // 7
        merkleTreeHex.add("8e97b4a311d2c58c019e93e9f943156fcef7dfd78973515d8f284f1b410158ba"); // 8
        // calculated part
        merkleTreeHex.add("308afe4e163a70120de2a641ec44342dcbd63b7a5d6789268c73899339265aee"); // 9
        merkleTreeHex.add("ba9ca9e20a8e88ba7be1673d9af056c288e1a8a9965594697f306485b54f8639"); // 10
        merkleTreeHex.add("199b6bb13e941afec48e4565ca980abd40e34ed3b9f10523db64fc3dce2fb7b8"); // 11
        merkleTreeHex.add("e29c65d08f8898b646fdde3e2cc1317e2c717f8f6485af9973ef41374a51a8e3"); // 12
        merkleTreeHex.add("9567fc20e47374adda00ee83d89ea9939798b282543ea6cbd8fb0023b57465ed"); // 13
        merkleTreeHex.add("e7f8bb5dbb398caf50adf1667b491ed61fe4d6f1f7624508ec36cb633bf5bf82"); // 14
        merkleTreeHex.add("61bfbdf7038dc7f21e2bcf193faef8e6caa8222af016a6ed86b9e9d860f046df"); // 15
        merkleTreeHex.add("d257fc7591d43caac41dfc70ca8326d4a1189f8012b9ab3cd52840dbd2f94430"); // 16
        merkleTreeHex.add("66dbf7e6a28135fd04a32bdd2d150c8585092670d48379ea8e68823f4362e2ab"); // 17
        merkleTreeHex.add("f6374a6dd9d838b1871e0ce8d4e537011aa79c60dc12ef80a472c43bc5021aeb"); // 18
        merkleTreeHex.add("29d000eee85f08b6482026be2d92d081d6f9418346e6b2e9fe2e9b985f24ed1e"); // 19 - merkle root

        // Set merkle path for leaf with idx 4
        ArrayList<Pair<Byte, byte[]>> merklePathSources = new ArrayList<>();
        merklePathSources.add(new Pair<>((byte)1, BytesUtils.fromHexString(merkleTreeHex.get(5))));
        merklePathSources.add(new Pair<>((byte)1, BytesUtils.fromHexString(merkleTreeHex.get(12))));
        merklePathSources.add(new Pair<>((byte)0, BytesUtils.fromHexString(merkleTreeHex.get(14))));
        merklePathSources.add(new Pair<>((byte)1, BytesUtils.fromHexString(merkleTreeHex.get(18))));

        MerklePath path = new MerklePath(merklePathSources);


        // Test 1: apply leaf with index 4 to its merkle path -> success
        String res = BytesUtils.toHexString(path.apply(BytesUtils.fromHexString(merkleTreeHex.get(4))));
        assertEquals("Leaf applied to merkle path expected to be same to merkle root.", merkleTreeHex.get(19), res);

        // Test 2: apply inappropriate leaf to merkle path -> fail
        res = BytesUtils.toHexString(path.apply(BytesUtils.fromHexString(merkleTreeHex.get(5))));
        assertNotEquals("Leaf applied to merkle path expected to be different to merkle root.", merkleTreeHex.get(19), res);
    }

    @Test
    public void MerklePathTest_SerializationTest() {

        ArrayList<Pair<Byte, byte[]>> merklePathSources = new ArrayList<>();
        merklePathSources.add(new Pair<>((byte)0, BytesUtils.fromHexString("29d000eee85f08b6482026be2d92d081d6f9418346e6b2e9fe2e9b985f24ed1e")));
        merklePathSources.add(new Pair<>((byte)1, BytesUtils.fromHexString("61bfbdf7038dc7f21e2bcf193faef8e6caa8222af016a6ed86b9e9d860f046df")));
        merklePathSources.add(new Pair<>((byte)1, BytesUtils.fromHexString("9567fc20e47374adda00ee83d89ea9939798b282543ea6cbd8fb0023b57465ed")));
        merklePathSources.add(new Pair<>((byte)0, BytesUtils.fromHexString("2d9b08361ac900cb55c71516c513db868f38536ebac65c6de16e6db26367c970")));

        // Test 1: serialization/deserialization of empty MerklePath
        MerklePath mp1 = new MerklePath(new ArrayList<>());
        byte[] bytes = mp1.bytes();

        MerklePath mp2 = (MerklePath) MerklePathSerializer.getSerializer().parseBytes(bytes);
        assertEquals("Empty Merkle Path expected.",0, mp2.merklePathList().size());
        assertEquals("Merkle Path hash codes expected to be equal.", mp1.hashCode(), mp2.hashCode());
        assertEquals("Merkle Pathes expected to be equal.", mp1, mp2);

        // Test 2: serialization/deserialization of non-empty MerklePath
        MerklePath mp3 = new MerklePath(merklePathSources);
        bytes = mp3.bytes();

        MerklePath mp4 = (MerklePath) MerklePathSerializer.getSerializer().parseBytes(bytes);
        assertEquals("Merkle Path list size expected to be 4.",4, mp4.merklePathList().size());
        assertEquals("Merkle Path hash codes expected to be equal.", mp3.hashCode(), mp4.hashCode());
        assertEquals("Merkle Pathes expected to be equal.", mp3, mp4);
        for(int i = 0; i < merklePathSources.size(); i++) {
            assertEquals(String.format("Merkle Path list item %d key expected to be equal.", i), merklePathSources.get(i).getKey(), mp4.merklePathList().get(i).getKey());

            assertEquals(String.format("Merkle Path list item %d value expected to be equal.", i),
                    true, Arrays.equals(merklePathSources.get(i).getValue(), mp4.merklePathList().get(i).getValue()));
        }
    }
}