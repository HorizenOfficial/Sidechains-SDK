package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/crypto"
	"libevm/test"
	"testing"
)

func TestRawStateDB(t *testing.T) {
	var (
		instance = New()
		addr     = common.HexToAddress("0x0011223344556677889900112233445566778899")
		key      = common.BytesToHash(crypto.Keccak256(common.FromHex("00112233")))
		value    = common.HexToHash("0x1234")
	)
	dbHandle := instance.OpenMemoryDB()
	_, db := instance.databases.Get(dbHandle)
	statedb, _ := state.New(test.ZeroHash, db.database, nil)
	initialCodeHash := statedb.GetCodeHash(addr)
	if initialCodeHash != test.ZeroHash {
		t.Errorf("code hash of non-existant account should be zero, got %v", initialCodeHash)
	}
	// set a non-empty value to the account to make sure it exists and is not "empty"
	statedb.SetNonce(addr, 1)
	emptyCodeHash := statedb.GetCodeHash(addr)
	if emptyCodeHash != test.NullHash {
		t.Errorf("code hash of empty account should be hash of nil, got %v", emptyCodeHash)
	}
	revid := statedb.Snapshot()
	statedb.SetState(addr, key, value)
	retrievedValue := statedb.GetState(addr, key)
	if retrievedValue != value {
		t.Error("value not set correctly")
	}
	statedb.RevertToSnapshot(revid)
	revertedValue := statedb.GetState(addr, key)
	if revertedValue != test.ZeroHash {
		t.Error("snapshot rollback failed")
	}
	statedb.SetState(addr, key, value)
	hash, _ := statedb.Commit(true)
	_ = statedb.Database().TrieDB().Commit(hash, false, nil)
	committedValue := statedb.GetState(addr, key)
	if committedValue != value {
		t.Error("value not committed correctly")
	}
}
