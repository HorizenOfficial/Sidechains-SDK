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
	original byte
	values   []byte
	refund   uint64
}{
	{0, []byte{0, 0}, 0},        // 0 -> 0 -> 0
	{0, []byte{0, 1}, 0},        // 0 -> 0 -> 1
	{0, []byte{1, 0}, 19900},    // 0 -> 1 -> 0
	{0, []byte{1, 2}, 0},        // 0 -> 1 -> 2
	{0, []byte{1, 1}, 0},        // 0 -> 1 -> 1
	{1, []byte{0, 0}, 4800},     // 1 -> 0 -> 0
	{1, []byte{0, 1}, 2800},     // 1 -> 0 -> 1
	{1, []byte{0, 2}, 0},        // 1 -> 0 -> 2
	{1, []byte{2, 0}, 4800},     // 1 -> 2 -> 0
	{1, []byte{2, 3}, 0},        // 1 -> 2 -> 3
	{1, []byte{2, 1}, 2800},     // 1 -> 2 -> 1
	{1, []byte{2, 2}, 0},        // 1 -> 2 -> 2
	{1, []byte{1, 0}, 4800},     // 1 -> 1 -> 0
	{1, []byte{1, 2}, 0},        // 1 -> 1 -> 2
	{1, []byte{1, 1}, 0},        // 1 -> 1 -> 1
	{0, []byte{1, 0, 1}, 19900}, // 0 -> 1 -> 0 -> 1
	{1, []byte{0, 1, 0}, 7600},  // 1 -> 0 -> 1 -> 0
}

func TestSetStateWithRefund(t *testing.T) {
	var (
		instance = New()
		address  = common.BytesToAddress([]byte("contract"))
		key      = common.Hash{}
	)
	for i, tt := range refundTests {
		var (
			original   = common.BytesToHash([]byte{tt.original})
			statedb, _ = state.New(common.Hash{}, state.NewDatabase(rawdb.NewMemoryDatabase()), nil)
		)
		statedb.CreateAccount(address)
		statedb.SetState(address, key, original)
		// Push the state into the "original" slot
		// note: we use "false" here because the test account would be removed otherwise as it is considered "empty",
		// setting a "fake" code hash would also prevent that
		statedb.Finalise(false)

		// make sure the original value was properly committed
		if originalTest := statedb.GetState(address, key); originalTest != original {
			t.Errorf("test %d: failed to set original value:\nhave %v\nwant %v", i, originalTest, original)
		}

		// write values in the order given
		for _, v := range tt.values {
			instance.setStateWithRefund(statedb, address, key, common.BytesToHash([]byte{v}))
		}

		// verify refund is as expected
		if refund := statedb.GetRefund(); refund != tt.refund {
			t.Errorf("test %d: gas refund mismatch: have %v, want %v", i, refund, tt.refund)
		}
	}
}
