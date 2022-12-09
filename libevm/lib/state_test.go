package lib

import (
	"bytes"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/crypto"
	"libevm/test"
	"math/big"
	"math/rand"
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
	// set a non-empty value to the account to make sure it exists and is not "empty"
	statedb.SetNonce(addr, 1)
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

func TestStateEmpty(t *testing.T) {
	checks := []struct {
		name          string
		shouldBeEmpty bool
		change        func(service *Service, account AccountParams)
	}{
		{"non-zero balance", false, func(service *Service, account AccountParams) {
			// set some non-zero balance
			_ = service.StateSetBalance(BalanceParams{
				AccountParams: account,
				Amount:        (*hexutil.Big)(big.NewInt(123)),
			})
		}},
		{"non-zero nonce", false, func(service *Service, account AccountParams) {
			// set a non-zero nonce
			_ = service.StateSetNonce(NonceParams{
				AccountParams: account,
				Nonce:         123,
			})
		}},
		{"non-nil code", false, func(service *Service, account AccountParams) {
			// set some non-nil code
			_ = service.StateSetCode(CodeParams{
				AccountParams: account,
				Code:          common.FromHex("0xdeadbeef"),
			})
		}},
		{"non-empty storage", true, func(service *Service, account AccountParams) {
			// "negative" test: even with data in the storage trie, accounts are still considered empty
			_ = service.StateSetStorage(SetStorageParams{
				StorageParams: StorageParams{
					AccountParams: account,
					Key:           common.BytesToHash(crypto.Keccak256(common.FromHex("00112233"))),
				},
				Value: common.HexToHash("0x1234"),
			})
		}},
	}

	for i, check := range checks {
		t.Run(check.name, func(t *testing.T) {
			instance, _, stateHandle := SetupTest()
			accountParams := AccountParams{
				HandleParams: HandleParams{
					Handle: stateHandle,
				},
				Address: common.BytesToAddress(crypto.Keccak256(big.NewInt(int64(i)).Bytes())),
			}
			// make sure the account is "empty"
			if _, empty := instance.StateEmpty(accountParams); !empty {
				t.Errorf("expected account to be empty: %v", accountParams.Address)
			}
			// apply given change
			check.change(instance, accountParams)
			// make sure empty-status matches expectation
			if _, isEmpty := instance.StateEmpty(accountParams); isEmpty != check.shouldBeEmpty {
				t.Errorf("unexpected account empty-status: want %v is %v", check.shouldBeEmpty, isEmpty)
			}
			// finalize will prune empty accounts
			_ = instance.StateFinalize(HandleParams{Handle: stateHandle})
			// make sure empty-status still matches expectation
			if _, isEmpty := instance.StateEmpty(accountParams); isEmpty != check.shouldBeEmpty {
				t.Errorf("unexpected account empty-status: want %v is %v", check.shouldBeEmpty, isEmpty)
			}
		})
	}
}

func TestStateBalances(t *testing.T) {
	var (
		instance, _, stateHandle = SetupTest()
		numbers                  = []*big.Int{big.NewInt(5), big.NewInt(9862374968), big.NewInt(0), big.NewInt(1000)}
		account                  = AccountParams{
			HandleParams: HandleParams{
				Handle: stateHandle,
			},
			Address: common.HexToAddress("1234"),
		}
	)
	check := func(want *big.Int) {
		if _, got := instance.StateGetBalance(account); got.ToInt().Cmp(want) != 0 {
			t.Fatalf("unexpected balance: want %v got %v", want, got.ToInt())
		}
	}
	current := new(big.Int)
	// verify initial balance
	check(current)
	for _, x := range numbers {
		// add x
		_ = instance.StateAddBalance(BalanceParams{
			AccountParams: account,
			Amount:        (*hexutil.Big)(x),
		})
		check(new(big.Int).Add(current, x))
		// subtract x
		_ = instance.StateSubBalance(BalanceParams{
			AccountParams: account,
			Amount:        (*hexutil.Big)(x),
		})
		check(current)
		// set to x
		_ = instance.StateSetBalance(BalanceParams{
			AccountParams: account,
			Amount:        (*hexutil.Big)(x),
		})
		check(x)
		// remember expected value for next iteration
		current.Set(x)
	}
}

func TestStateNonce(t *testing.T) {
	var (
		instance, _, stateHandle = SetupTest()
		numbers                  = []uint64{123, 3123123151, 0, 6}
		account                  = AccountParams{
			HandleParams: HandleParams{
				Handle: stateHandle,
			},
			Address: common.HexToAddress("1234"),
		}
	)
	check := func(want uint64) {
		if _, got := instance.StateGetNonce(account); uint64(got) != want {
			t.Fatalf("unexpected nonce: want %v got %v", want, got)
		}
	}
	current := uint64(0)
	// verify initial nonce
	check(current)
	for _, x := range numbers {
		_ = instance.StateSetNonce(NonceParams{
			AccountParams: account,
			Nonce:         (hexutil.Uint64)(x),
		})
		check(x)
	}
}

func TestCodeAndCodeHash(t *testing.T) {
	var (
		instance, _, stateHandle = SetupTest()
		codes                    = [][]byte{
			common.FromHex("0xdeadbeef"),
			common.FromHex("0x01020304"),
			common.FromHex("0xdeadbeef"),
		}
		account = AccountParams{
			HandleParams: HandleParams{
				Handle: stateHandle,
			},
			Address: common.HexToAddress("1234"),
		}
	)
	check := func(wantCode []byte, wantHash common.Hash) {
		if _, got := instance.StateGetCode(account); bytes.Compare(got, wantCode) != 0 {
			t.Fatalf("unexpected code: want %v got %v", common.Bytes2Hex(wantCode), common.Bytes2Hex(got))
		}
		if _, got := instance.StateGetCodeHash(account); got != wantHash {
			t.Fatalf("unexpected code hash: want %v got %v", wantHash, got)
		}
	}
	// verify code hash of empty account is zeroes
	check(nil, test.ZeroHash)
	_ = instance.StateSetNonce(NonceParams{
		AccountParams: account,
		Nonce:         1,
	})
	// verify code hash of non-empty account is hash of nil
	check(nil, test.NullHash)
	// verify code hashes are as expected
	for _, x := range codes {
		_ = instance.StateSetCode(CodeParams{
			AccountParams: account,
			Code:          x,
		})
		check(x, crypto.Keccak256Hash(x))
	}
}

func TestSnapshot(t *testing.T) {
	var (
		instance, _, stateHandle = SetupTest()
		addr                     = common.HexToAddress("0x0011223344556677889900112233445566778899")
		key                      = common.BytesToHash(crypto.Keccak256(common.FromHex("00112233")))
		txHash                   = common.BytesToHash(crypto.Keccak256(common.FromHex("4321")))
		account                  = AccountParams{
			HandleParams: HandleParams{Handle: stateHandle},
			Address:      addr,
		}
		storageKey = StorageParams{
			AccountParams: account,
			Key:           key,
		}
	)

	// use constant seed to have reproducable results
	rand.Seed(42)

	_ = instance.StateSetTxContext(SetTxContextParams{
		HandleParams: HandleParams{Handle: stateHandle},
		TxHash:       txHash,
		TxIndex:      0,
	})

	type snapState struct {
		revid        int
		nonce        uint64
		balance      *big.Int
		code         []byte
		codeHash     common.Hash
		storageValue common.Hash
		logs         int
	}
	var stack []snapState

	// generate a bunch of snapshots at different states
	for len(stack) < 100 {
		// take snapshot and push current state
		_, revid := instance.StateSnapshot(account.HandleParams)
		_, nonce := instance.StateGetNonce(account)
		_, balance := instance.StateGetBalance(account)
		_, code := instance.StateGetCode(account)
		_, codeHash := instance.StateGetCodeHash(account)
		_, storageValue := instance.StateGetStorage(storageKey)
		_, logs := instance.StateGetLogs(GetLogsParams{
			HandleParams: account.HandleParams,
			TxHash:       txHash,
		})
		check := snapState{
			revid,
			uint64(nonce),
			balance.ToInt(),
			code,
			codeHash,
			storageValue,
			len(logs),
		}
		stack = append(stack, check)
		t.Logf("pushed snapshot: %v storage: %v", check.revid, check.storageValue.TerminalString())
		// modify account
		_ = instance.StateSetNonce(NonceParams{
			AccountParams: account,
			Nonce:         hexutil.Uint64(rand.Int63()),
		})
		_ = instance.StateSetBalance(BalanceParams{
			AccountParams: account,
			Amount:        (*hexutil.Big)(big.NewInt(rand.Int63())),
		})
		_ = instance.StateSetCode(CodeParams{
			AccountParams: account,
			Code:          test.RandomBytes(rand.Intn(25000)),
		})
		_ = instance.StateSetStorage(SetStorageParams{
			StorageParams: storageKey,
			Value:         test.RandomHash(),
		})
		_ = instance.StateAddLog(AddLogParams{
			AccountParams: account,
			Topics:        []common.Hash{test.RandomHash()},
			Data:          test.RandomBytes(rand.Intn(100)),
		})
	}

	// revert to some snapshots and verify
	for len(stack) > 0 {
		// pick a state to revert to
		n := len(stack) - 3
		if n < 0 {
			n = 0
		}
		check := stack[n]
		// remove elements after n from stack
		stack = stack[:n]
		// revert to snapshot
		_ = instance.StateRevertToSnapshot(SnapshotParams{
			HandleParams: HandleParams{Handle: stateHandle},
			RevisionId:   check.revid,
		})
		// verify state
		if _, nonce := instance.StateGetNonce(account); check.nonce != uint64(nonce) {
			t.Fatalf("unexpected value for nonce: want %v got %v", check.nonce, nonce)
		}
		if _, balance := instance.StateGetBalance(account); check.balance.Cmp(balance.ToInt()) != 0 {
			t.Fatalf("unexpected value for balance: want %v got %v", check.nonce, balance)
		}
		if _, code := instance.StateGetCode(account); bytes.Compare(check.code, code) != 0 {
			t.Fatalf("unexpected value for code: want %v got %v", common.Bytes2Hex(check.code), common.Bytes2Hex(code))
		}
		if _, codeHash := instance.StateGetCodeHash(account); check.codeHash != codeHash {
			t.Fatalf("unexpected value for nonce: want %v got %v", check.codeHash, codeHash)
		}
		if _, storageValue := instance.StateGetStorage(storageKey); check.storageValue != storageValue {
			t.Fatalf("unexpected value for nonce: want %v got %v", check.storageValue, storageValue)
		}
		if _, logs := instance.StateGetLogs(GetLogsParams{HandleParams: account.HandleParams, TxHash: txHash}); check.logs != len(logs) {
			t.Fatalf("unexpected number of logs: want %v got %v", check.logs, len(logs))
		}
		t.Logf("popped and validated snapshot: %v storage: %v", check.revid, check.storageValue.TerminalString())
	}

	// an invalid revision should panic when trying to revert to it
	defer func() {
		if r := recover(); r != nil {
			t.Logf("recovered from expected panic: %v", r)
		}
	}()
	_ = instance.StateRevertToSnapshot(SnapshotParams{
		HandleParams: HandleParams{Handle: stateHandle},
		RevisionId:   5,
	})
	t.Fatalf("expected a panic, should not have reached here")
}
