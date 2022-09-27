package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"libevm/test"
	"math/big"
	"testing"
)

func TestEvmTrace(t *testing.T) {
	var (
		instance     = New()
		err          error
		initialValue = common.Big0
		sender       = common.HexToAddress("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b")
	)
	err, dbHandle := instance.OpenMemoryDB()
	if err != nil {
		t.Fatal(err)
	}
	defer func() {
		_ = instance.CloseDatabase(DatabaseParams{DatabaseHandle: dbHandle})
	}()
	err, stateDbHandle := instance.StateOpen(StateParams{
		DatabaseParams: DatabaseParams{
			DatabaseHandle: dbHandle,
		},
		Root: common.Hash{},
	})
	if err != nil {
		t.Fatal(err)
	}
	err, result := instance.EvmApply(EvmParams{
		HandleParams: HandleParams{
			Handle: stateDbHandle,
		},
		From:  sender,
		To:    nil,
		Input: test.StorageContractDeploy(initialValue),
		Context: EvmContext{
			Coinbase: common.Address{},
			BaseFee:  (*hexutil.Big)(new(big.Int)),
		},
		TxTraceParams: &TraceParams{
			EnableMemory:     true,
			DisableStack:     false,
			DisableStorage:   false,
			EnableReturnData: true,
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	// do a coarse correctness check that does not immediately break on different versions of the solidity compiler
	if minimum, actual := 130, len(result.TraceLogs); minimum > actual {
		t.Fatalf("unexpected number of trace logs: expected at least %d, actual %d", minimum, actual)
	}
	// cherry-pick the one SSTORE instruction that should be in there
	sstoreInstructions := 0
	for _, trace := range result.TraceLogs {
		if trace.Op != "SSTORE" {
			continue
		}
		sstoreInstructions += 1
		if expected, actual := "SSTORE", trace.Op; expected != actual {
			t.Fatalf("unexpected op code: expected %s, actual %s", expected, actual)
		}
		if expected, actual := 1, len(*trace.Storage); expected != actual {
			t.Fatalf("unexpected number of accessed storage keys: expected %d, actual %d", expected, actual)
		}
	}
	if sstoreInstructions != 1 {
		t.Fatalf("unexpected number of SSTORE instructions: expected %d, actual %d", 1, sstoreInstructions)
	}
}
