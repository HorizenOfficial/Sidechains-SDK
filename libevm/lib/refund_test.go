package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"testing"
)

func TestRefunds(t *testing.T) {
	var (
		instance = New()
		sequence = []int{0, 67, 123, -20, 523, 56234, 41212, -23425, 1, 0}
	)
	dbHandle := instance.OpenMemoryDB()
	_, stateHandle := instance.StateOpen(StateParams{
		DatabaseParams: DatabaseParams{DatabaseHandle: dbHandle},
		Root:           common.Hash{},
	})
	handle := HandleParams{Handle: stateHandle}
	expected := uint64(0)
	for _, value := range sequence {
		if value >= 0 {
			expected += uint64(value)
			_ = instance.RefundAdd(RefundParams{
				HandleParams: handle,
				Gas:          (hexutil.Uint64)(value),
			})
		} else {
			expected -= uint64(-value)
			_ = instance.RefundSub(RefundParams{
				HandleParams: handle,
				Gas:          (hexutil.Uint64)(-value),
			})
		}
		err, refund := instance.RefundGet(handle)
		if err != nil {
			t.Error("failed to get refund", err)
		}
		if uint64(refund) != expected {
			t.Errorf("unexpected refund value: want 0 have %v", uint64(refund))
		}
	}
	_ = instance.StateFinalize(handle)
	if _, refund := instance.RefundGet(handle); refund != 0 {
		t.Error("finalize should reset refund counter")
	}
}
