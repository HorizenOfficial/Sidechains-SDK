package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/rawdb"
	"github.com/ethereum/go-ethereum/core/state"
	"testing"
)

// test cases from EIP-3529:
// https://github.com/ethereum/EIPs/blob/master/EIPS/eip-3529.md#with-reduced-refunds
var refundTests = []struct {
	sequence string
	refund   uint64
}{
	{"0x000000", 0},       // 0 -> 0 -> 0
	{"0x000001", 0},       // 0 -> 0 -> 1
	{"0x000100", 19900},   // 0 -> 1 -> 0
	{"0x000102", 0},       // 0 -> 1 -> 2
	{"0x000101", 0},       // 0 -> 1 -> 1
	{"0x010000", 4800},    // 1 -> 0 -> 0
	{"0x010001", 2800},    // 1 -> 0 -> 1
	{"0x010002", 0},       // 1 -> 0 -> 2
	{"0x010200", 4800},    // 1 -> 2 -> 0
	{"0x010203", 0},       // 1 -> 2 -> 3
	{"0x010201", 2800},    // 1 -> 2 -> 1
	{"0x010202", 0},       // 1 -> 2 -> 2
	{"0x010100", 4800},    // 1 -> 1 -> 0
	{"0x010102", 0},       // 1 -> 1 -> 2
	{"0x010101", 0},       // 1 -> 1 -> 1
	{"0x00010001", 19900}, // 0 -> 1 -> 0 -> 1
	{"0x01000100", 7600},  // 1 -> 0 -> 1 -> 0
}

func TestSetStateWithRefund(t *testing.T) {
	var (
		instance = New()
		address  = common.BytesToAddress([]byte("contract"))
		key      = common.Hash{}
	)
	for i, tt := range refundTests {
		// decode hex string to a list of values
		var values []common.Hash
		for _, value := range common.FromHex(tt.sequence) {
			values = append(values, common.BytesToHash([]byte{value}))
		}

		// set and commit the first value
		statedb, _ := state.New(common.Hash{}, state.NewDatabase(rawdb.NewMemoryDatabase()), nil)
		statedb.CreateAccount(address)
		statedb.SetState(address, key, values[0])
		// Push the state into the "original" slot
		// note: we use "false" here because the test account would be removed otherwise as it is considered "empty",
		// setting a "fake" code hash would also prevent that
		statedb.Finalise(false)

		// make sure the original value was properly committed
		if original := statedb.GetState(address, key); original != values[0] {
			t.Errorf("test %d: failed to set original value:\nhave %v\nwant %v", i, original, values[0])
		}

		// set all the remaining values in the order given
		for _, value := range values[1:] {
			instance.setStateWithRefund(statedb, address, key, value)
		}

		// verify the refund is as expected
		if refund := statedb.GetRefund(); refund != tt.refund {
			t.Errorf("test %d: gas refund mismatch: have %v, want %v", i, refund, tt.refund)
		}
	}
}
