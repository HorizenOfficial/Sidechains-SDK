package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/crypto"
	"testing"
)

func TestService(t *testing.T) {
	var (
		err   error
		addr  = common.HexToAddress("0x0011223344556677889900112233445566778899")
		key   = common.BytesToHash(crypto.Keccak256(common.Hex2Bytes("00112233")))
		value = common.HexToHash("0x1234")
	)
	instance := New()
	err = instance.OpenMemoryDB()
	if err != nil {
		t.Error(err)
	}
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
	if retrievedValue != value {
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
	if committedValue != value {
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
	if writtenValue != value {
		t.Error("value not written correctly")
	}
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
