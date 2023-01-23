package geth_internal

import (
	"bytes"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/rawdb"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/crypto"
	"libevm/test"
	"math/big"
	"testing"
)

// the test here is adapted from the geth-internal one, see:
// github.com/ethereum/go-ethereum@v1.10.26/ethclient/gethclient/gethclient_test.go:200

var (
	testKey, _  = crypto.HexToECDSA("b71c71a67e1177ad4e901695e1b4b9ee17ae16c6668d313eac2f96dbcda3f291")
	testAddr    = crypto.PubkeyToAddress(testKey.PublicKey)
	testSlot    = common.HexToHash("0xdeadbeef")
	testValue   = crypto.Keccak256Hash(testSlot[:])
	testBalance = big.NewInt(2e15)
	testNonce   = uint64(27)
)

func TestGetProof(t *testing.T) {
	statedb, err := state.New(test.ZeroHash, state.NewDatabase(rawdb.NewMemoryDatabase()), nil)
	if err != nil {
		t.Fatalf("failed to create statedb: %v", err)
	}

	// prepare test account
	statedb.SetNonce(testAddr, testNonce)
	statedb.SetBalance(testAddr, testBalance)
	statedb.SetStorage(testAddr, map[common.Hash]common.Hash{testSlot: testValue})
	_, _ = statedb.Commit(true)

	// get proof
	result, err := GetProof(statedb, testAddr, []string{testSlot.String()})
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(result.Address[:], testAddr[:]) {
		t.Fatalf("unexpected address, want: %v got: %v", testAddr, result.Address)
	}
	// test nonce
	nonce := statedb.GetNonce(result.Address)
	if uint64(result.Nonce) != nonce {
		t.Fatalf("invalid nonce, want: %v got: %v", nonce, result.Nonce)
	}
	// test balance
	balance := statedb.GetBalance(result.Address)
	if result.Balance.ToInt().Cmp(balance) != 0 {
		t.Fatalf("invalid balance, want: %v got: %v", balance, result.Balance)
	}
	// test storage
	if len(result.StorageProof) != 1 {
		t.Fatalf("invalid storage proof, want 1 proof, got %v proof(s)", len(result.StorageProof))
	}
	proof := result.StorageProof[0]
	slotValue := statedb.GetState(testAddr, testSlot)
	if !bytes.Equal(slotValue.Bytes(), proof.Value.ToInt().Bytes()) {
		t.Fatalf("invalid storage proof value, want: %v, got: %v", slotValue, proof.Value)
	}
	if proof.Key != testSlot.String() {
		t.Fatalf("invalid storage proof key, want: %v, got: %v", testSlot.String(), proof.Key)
	}
}
