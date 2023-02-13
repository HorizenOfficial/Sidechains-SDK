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
	_ = instance.StateSetCode(CodeParams{
		AccountParams: account,
		Code:          crypto.Keccak256Hash(addr.Bytes()).Bytes(),
	})
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

	err = instance.StateSetStorage(SetStorageParams{StorageParams: storage})
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
