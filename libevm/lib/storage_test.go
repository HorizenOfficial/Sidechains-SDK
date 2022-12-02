package lib

import (
	"bytes"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/crypto"
	"testing"
)

func checkValue(t *testing.T, expected []byte, actual []byte) {
	if !bytes.Equal(expected, actual) {
		t.Errorf(
			"storage value mismatch:\n"+
				"expected: %v\n"+
				"actual:   %v",
			common.Bytes2Hex(expected),
			common.Bytes2Hex(actual),
		)
	}
}

func testStorageSetCommitWrite(t *testing.T, instance *Service, dbHandle int, addr common.Address, key common.Hash, value common.Hash) {
	var err error
	err, handleInt := instance.StateOpen(StateParams{
		DatabaseParams: DatabaseParams{DatabaseHandle: dbHandle},
		Root:           common.Hash{},
	})
	if err != nil {
		t.Error(err)
	}
	handle := HandleParams{
		Handle: handleInt,
	}
	account := AccountParams{
		HandleParams: handle,
		Address:      addr,
	}
	storage := StorageParams{
		AccountParams: account,
		Key:           key,
	}
	// make sure the account is not "empty"
	if _, empty := instance.StateEmpty(account); !empty {
		t.Errorf("expected account to be empty: %v", err)
	}
	_ = instance.StateSetCode(CodeParams{
		AccountParams: account,
		Code:          crypto.Keccak256Hash(addr.Bytes()).Bytes(),
	})
	if _, empty := instance.StateEmpty(account); empty {
		t.Errorf("expected account to be non-empty: %v", err)
	}
	err, initialRoot := instance.StateIntermediateRoot(handle)
	err = instance.StateSetStorage(SetStorageParams{
		StorageParams: storage,
		Value:         value,
	})
	if err != nil {
		t.Error(err)
	}
	err, retrievedValue := instance.StateGetStorage(storage)
	if err != nil {
		t.Error(err)
	}
	checkValue(t, value.Bytes(), retrievedValue.Bytes())
	err, committedRoot := instance.StateCommit(handle)
	if err != nil {
		t.Error(err)
	}
	err, committedValue := instance.StateGetStorage(storage)
	if err != nil {
		t.Error(err)
	}
	checkValue(t, value.Bytes(), committedValue.Bytes())
	instance.StateClose(handle)

	// reopen state at the committed root
	err, handleInt = instance.StateOpen(StateParams{
		DatabaseParams: DatabaseParams{DatabaseHandle: dbHandle},
		Root:           committedRoot,
	})
	if err != nil {
		t.Error(err)
	}
	// update helpers with new handle
	handle.Handle = handleInt
	account.Handle = handleInt
	storage.Handle = handleInt

	err, writtenValue := instance.StateGetStorage(storage)
	if err != nil {
		t.Error(err)
	}
	checkValue(t, value.Bytes(), writtenValue.Bytes())

	err = instance.StateRemoveStorage(storage)
	if err != nil {
		t.Error(err)
	}
	err, removedRoot := instance.StateIntermediateRoot(handle)
	if removedRoot != initialRoot {
		t.Error("incomplete removal of state storage")
	}

	instance.StateClose(handle)
}

func testStorageBytesSetCommitWrite(t *testing.T, instance *Service, dbHandle int, addr common.Address, key common.Hash, value []byte) {
	var err error
	err, handleInt := instance.StateOpen(StateParams{
		DatabaseParams: DatabaseParams{DatabaseHandle: dbHandle},
		Root:           common.Hash{}})
	if err != nil {
		t.Error(err)
	}
	handle := HandleParams{
		Handle: handleInt,
	}
	account := AccountParams{
		HandleParams: handle,
		Address:      addr,
	}
	storage := StorageParams{
		AccountParams: account,
		Key:           key,
	}
	// make sure the account is not "empty"
	_ = instance.StateSetCode(CodeParams{
		AccountParams: account,
		Code:          crypto.Keccak256Hash(addr.Bytes()).Bytes(),
	})
	err, initialRoot := instance.StateIntermediateRoot(handle)
	err = instance.StateSetStorageBytes(SetStorageBytesParams{
		StorageParams: storage,
		Value:         value,
	})
	if err != nil {
		t.Error(err)
	}
	err, retrievedValue := instance.StateGetStorageBytes(storage)
	if err != nil {
		t.Error(err)
	}
	checkValue(t, value, retrievedValue)
	err, committedRoot := instance.StateCommit(handle)
	if err != nil {
		t.Error(err)
	}
	err, committedValue := instance.StateGetStorageBytes(storage)
	if err != nil {
		t.Error(err)
	}
	checkValue(t, value, committedValue)
	instance.StateClose(handle)

	// reopen state at the committed root
	err, handleInt = instance.StateOpen(StateParams{
		DatabaseParams: DatabaseParams{DatabaseHandle: dbHandle},
		Root:           committedRoot,
	})
	if err != nil {
		t.Error(err)
	}
	// update helpers with new handle
	handle.Handle = handleInt
	account.Handle = handleInt
	storage.Handle = handleInt

	err, writtenValue := instance.StateGetStorageBytes(storage)
	if err != nil {
		t.Error(err)
	}
	checkValue(t, value, writtenValue)

	err = instance.StateRemoveStorageBytes(storage)
	if err != nil {
		t.Error(err)
	}
	err, removedRoot := instance.StateIntermediateRoot(handle)
	if removedRoot != initialRoot {
		t.Error("incomplete removal of state storage")
	}

	instance.StateClose(handle)
}

func TestStateStorage(t *testing.T) {
	var (
		instance = New()
		addr     = common.HexToAddress("0x0011223344556677889900112233445566778899")
		key      = common.BytesToHash(crypto.Keccak256(common.FromHex("44556677")))
		values   = []common.Hash{
			common.HexToHash("1234"),
			common.HexToHash("5ac11866e5b303e74e94652f3fdcf44d292f2f82"),
			common.HexToHash("5ac11866e36c8d1a9195cb74789a97e15b303e74e94652f3fdcf44d292f2f829"),
			common.HexToHash("572f59c7e67c4e1733b2a349db3fc3f50e7b6d2d2b08419957937678d647b40a47"),
		}
	)
	dbHandle := instance.OpenMemoryDB()
	for _, value := range values {
		testStorageSetCommitWrite(t, instance, dbHandle, addr, key, value)
	}
}

func TestStateStorageBytes(t *testing.T) {
	var (
		instance = New()
		addr     = common.HexToAddress("0x0011223344556677889900112233445566778899")
		key      = common.BytesToHash(crypto.Keccak256(common.FromHex("00112233")))
		values   = [][]byte{
			common.FromHex("1234"),
			common.FromHex("5ac11866e36c8d1a9195cb74789a97e15b303e74e94652f3fdcf44d292f2f829"),
			common.FromHex("572f59c7e67c4e1733b2a349db3fc3f50e7b6d2d2b08419957937678d647b40a4706f4668e14d832a9e239cc5f22b93988e2238b469bdd1654d4445111d2d7cb1481a443645c9d2563e15d6a577cc89295c69f5e0165"),
			common.FromHex("c359e9d0c2cffce02fc6e55e637cef893595294bfea05ad48a54d5659a8fdb552797c0214354aa4d0cde268d9fe046f321760a65beb6e8a17d6407f5f7d74c3bec075049f764f184599dc4fae3e0f1acf4aadd70fea64eb71131ee1f7f522a7b960c1a344a8c58e657e66033af7a3f67a80b9e459af9107ba19825a3991bc930e62a1a912cbe808b89c795ab3b6b098d25759925ad85c2eb25646a245cef8fe32417d549a462560b831e07786335103eaa4ea4404cc9ee844c6471ca721d864f45014dc7a41cf91845722e28346e115f4acc3acd9203881f4101964243ce46a66e2f7166465893b4f9097c842e67533b1292d78f42767be0a71f1b797b3ecfa19bef54d3b33f31571ba9e3ccf20399c1493361c517a3517be7916fb4413342a133c5c5df6e2649b5a4dc949f04356ac8be51192335813a1bb7bb612ab29f27a1decdc3232075f213b3357404437e529c790589b2934d409c89bf7132e3786b8b0592d8cd5c306d7ec3cc0b2e413ee1057e4893dbbdf74d92915a176ae7c9606a262d923f9baaeea7aefbf2be499348a15a2c6fe9a26cad513d3a3a9afba638fbf27181fa93fbe4864144c7d6aa6e27df2668ad50adbce634237728427b6db5a1c7b3574a4dda57b08026142db7a607b48c2149eb2670429c5cf3258c61f01a264893dbf951c40e707e5d3b13693a6c35c0760d32aad8a964937acabbd47e24ff3bc3d4473b6dd97bde69ad194f2eaa39b28530a00ea05de0824d12f5c45cbf737b1ef06839571340447d56b307ad1053f9211734d5a49e5d2b8a5642f9d769fb2bee816026d7c3f732060a0bf7e6db832ff033b59cd4cb36d0b04b2390d4c06f56c7a7ac5ecf0ea3dba316c8e153668ce4d1532c5443c6501fc02508c89b14029a"),
		}
	)
	dbHandle := instance.OpenMemoryDB()
	for _, value := range values {
		testStorageBytesSetCommitWrite(t, instance, dbHandle, addr, key, value)
	}
}
