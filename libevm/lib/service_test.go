package lib

import (
	"bytes"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/crypto"
	"testing"
)

func testStorageSetCommitWrite(t *testing.T, instance *Service, addr common.Address, key common.Hash, value []byte) {
	var err error
	err, handle := instance.StateOpen(StateRootParams{Root: common.Hash{}})
	if err != nil {
		t.Error(err)
	}
	err = instance.StateSetStorage(SetStorageParams{
		StorageParams: StorageParams{
			AccountParams: AccountParams{
				HandleParams: HandleParams{
					Handle: handle,
				},
				Address: addr,
			},
			Key: key,
		},
		Value: value,
	})
	if err != nil {
		t.Error(err)
	}
	err, retrievedValue := instance.StateGetStorage(StorageParams{
		AccountParams: AccountParams{
			HandleParams: HandleParams{
				Handle: handle,
			},
			Address: addr,
		},
		Key: key,
	})
	if err != nil {
		t.Error(err)
	}
	if !bytes.Equal(retrievedValue, value) {
		t.Error("value not stored correctly")
	}
	err, root := instance.StateCommit(HandleParams{
		Handle: handle,
	})
	if err != nil {
		t.Error(err)
	}
	err, committedValue := instance.StateGetStorage(StorageParams{
		AccountParams: AccountParams{
			HandleParams: HandleParams{
				Handle: handle,
			},
			Address: addr,
		},
		Key: key,
	})
	if err != nil {
		t.Error(err)
	}
	if !bytes.Equal(committedValue, value) {
		t.Error("value not committed correctly")
	}
	instance.StateClose(HandleParams{Handle: handle})

	err, handle = instance.StateOpen(StateRootParams{Root: root})
	if err != nil {
		t.Error(err)
	}
	err, writtenValue := instance.StateGetStorage(StorageParams{
		AccountParams: AccountParams{
			HandleParams: HandleParams{
				Handle: handle,
			},
			Address: addr,
		},
		Key: key,
	})
	if err != nil {
		t.Error(err)
	}
	if !bytes.Equal(writtenValue, value) {
		t.Error("value not written correctly")
	}
}

func TestStateStorage(t *testing.T) {
	var (
		err            error
		addr           = common.HexToAddress("0x0011223344556677889900112233445566778899")
		key            = common.BytesToHash(crypto.Keccak256(common.Hex2Bytes("00112233")))
		smallValue     = common.Hex2Bytes("1234")
		mediumValue    = common.Hex2Bytes("5ac11866e36c8d1a9195cb74789a97e15b303e74e94652f3fdcf44d292f2f829")
		bigValue       = common.Hex2Bytes("572f59c7e67c4e1733b2a349db3fc3f50e7b6d2d2b08419957937678d647b40a4706f4668e14d832a9e239cc5f22b93988e2238b469bdd1654d4445111d2d7cb1481a443645c9d2563e15d6a577cc89295c69f5e0165")
		reallyBigValue = common.Hex2Bytes("c359e9d0c2cffce02fc6e55e637cef893595294bfea05ad48a54d5659a8fdb552797c0214354aa4d0cde268d9fe046f321760a65beb6e8a17d6407f5f7d74c3bec075049f764f184599dc4fae3e0f1acf4aadd70fea64eb71131ee1f7f522a7b960c1a344a8c58e657e66033af7a3f67a80b9e459af9107ba19825a3991bc930e62a1a912cbe808b89c795ab3b6b098d25759925ad85c2eb25646a245cef8fe32417d549a462560b831e07786335103eaa4ea4404cc9ee844c6471ca721d864f45014dc7a41cf91845722e28346e115f4acc3acd9203881f4101964243ce46a66e2f7166465893b4f9097c842e67533b1292d78f42767be0a71f1b797b3ecfa19bef54d3b33f31571ba9e3ccf20399c1493361c517a3517be7916fb4413342a133c5c5df6e2649b5a4dc949f04356ac8be51192335813a1bb7bb612ab29f27a1decdc3232075f213b3357404437e529c790589b2934d409c89bf7132e3786b8b0592d8cd5c306d7ec3cc0b2e413ee1057e4893dbbdf74d92915a176ae7c9606a262d923f9baaeea7aefbf2be499348a15a2c6fe9a26cad513d3a3a9afba638fbf27181fa93fbe4864144c7d6aa6e27df2668ad50adbce634237728427b6db5a1c7b3574a4dda57b08026142db7a607b48c2149eb2670429c5cf3258c61f01a264893dbf951c40e707e5d3b13693a6c35c0760d32aad8a964937acabbd47e24ff3bc3d4473b6dd97bde69ad194f2eaa39b28530a00ea05de0824d12f5c45cbf737b1ef06839571340447d56b307ad1053f9211734d5a49e5d2b8a5642f9d769fb2bee816026d7c3f732060a0bf7e6db832ff033b59cd4cb36d0b04b2390d4c06f56c7a7ac5ecf0ea3dba316c8e153668ce4d1532c5443c6501fc02508c89b14029a")
	)
	instance := New()
	err = instance.OpenMemoryDB()
	if err != nil {
		t.Error(err)
	}
	testStorageSetCommitWrite(t, instance, addr, key, smallValue)
	testStorageSetCommitWrite(t, instance, addr, key, mediumValue)
	testStorageSetCommitWrite(t, instance, addr, key, bigValue)
	testStorageSetCommitWrite(t, instance, addr, key, reallyBigValue)
}

func TestStateDBdirectly(t *testing.T) {
	var (
		addr     = common.HexToAddress("0x0011223344556677889900112233445566778899")
		key      = common.BytesToHash(crypto.Keccak256(common.Hex2Bytes("00112233")))
		value    = common.HexToHash("0x1234")
		instance = New()
	)
	_ = instance.OpenLevelDB(LevelDBParams{Path: t.TempDir()})
	statedb, _ := state.New(common.Hash{}, instance.database, nil)
	statedb.SetState(addr, key, value)
	statedb.SetNonce(addr, 1)
	retrievedValue := statedb.GetState(addr, key)
	if retrievedValue != value {
		t.Error("value not set correctly")
	}
	hash, _ := statedb.Commit(true)
	_ = statedb.Database().TrieDB().Commit(hash, false, nil)
	committedValue := statedb.GetState(addr, key)
	if committedValue != value {
		t.Error("value not committed correctly")
	}
}
