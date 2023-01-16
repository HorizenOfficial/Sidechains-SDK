package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"libevm/test"
	"math/big"
	"reflect"
	"testing"
)

func TestEvmTrace(t *testing.T) {
	var (
		instance     = New()
		err          error
		initialValue = common.Big0
		sender       = common.HexToAddress("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b")
	)
	dbHandle := instance.OpenMemoryDB()
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
		TraceOptions: &TraceOptions{
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

func TestEvmOpCodes(t *testing.T) {
	var (
		instance = New()
		user     = common.HexToAddress("0x42")
	)
	dbHandle := instance.OpenMemoryDB()
	_, stateHandle := instance.StateOpen(StateParams{
		DatabaseParams: DatabaseParams{
			dbHandle,
		},
		Root: test.EmptyHash,
	})
	_, statedb := instance.statedbs.Get(stateHandle)

	// deploy "OpCodes" contract
	_, resultDeploy := instance.EvmApply(EvmParams{
		HandleParams: HandleParams{Handle: stateHandle},
		From:         user,
		To:           nil,
		Input:        test.OpCodesContractDeploy(),
		AvailableGas: 200000,
	})
	if resultDeploy.EvmError != "" {
		t.Fatalf("vm error: %v", resultDeploy.EvmError)
	}
	deployedCode := statedb.GetCode(*resultDeploy.ContractAddress)
	if common.Bytes2Hex(test.OpCodesContractRuntimeCode()) != common.Bytes2Hex(deployedCode) {
		t.Fatalf("deployed code does not match %s", common.Bytes2Hex(deployedCode))
	}

	var (
		gasPrice    = big.NewInt(586732)
		chainID     = uint64(1337)
		coinbase    = common.HexToAddress("0x09a1e4d0c6f6055287a6e1553a1d9cfe05767591")
		gasLimit    = uint64(32123457)
		blockNumber = big.NewInt(51231287)
		time        = big.NewInt(1669144595)
		baseFee     = big.NewInt(123872)
		random      = common.HexToHash("0x0a5d85d0f0e021c04643e05e38f8f28029275683ee743910670154d78322b6eb")
	)

	// redefine this interface here, because it is not exported from GETH
	type bytesBacked interface {
		Bytes() []byte
	}

	checks := []struct {
		name     string
		expected bytesBacked
	}{
		{"GASPRICE", gasPrice},
		{"CHAINID", new(big.Int).SetUint64(chainID)},
		{"COINBASE", coinbase},
		{"GASLIMIT", new(big.Int).SetUint64(gasLimit)},
		{"BLOCKNUMBER", blockNumber},
		{"TIME", time},
		{"BASEFEE", baseFee},
		{"RANDOM", random},
	}

	for _, check := range checks {
		t.Run(check.name, func(t *testing.T) {
			// call function and verify result value
			err, result := instance.EvmApply(EvmParams{
				HandleParams: HandleParams{Handle: stateHandle},
				From:         user,
				To:           resultDeploy.ContractAddress,
				Input:        test.OpCodesContractCall(check.name),
				AvailableGas: 200000,
				GasPrice:     (*hexutil.Big)(gasPrice),
				Context: EvmContext{
					ChainID:     hexutil.Uint64(chainID),
					Coinbase:    coinbase,
					GasLimit:    hexutil.Uint64(gasLimit),
					BlockNumber: (*hexutil.Big)(blockNumber),
					Time:        (*hexutil.Big)(time),
					BaseFee:     (*hexutil.Big)(baseFee),
					Random:      &random,
				},
			})
			if err != nil {
				t.Fatalf("error: %v", result.EvmError)
			}
			if result.EvmError != "" {
				t.Fatalf("vm error: %v", result.EvmError)
			}
			if expected := common.LeftPadBytes(check.expected.Bytes(), 32); !reflect.DeepEqual(expected, result.ReturnData) {
				t.Fatalf("test failed for %v:\n%v expected\n%v actual", check.name, expected, result.ReturnData)
			}
		})
	}
}

func TestEvmErrors(t *testing.T) {
	var (
		instance = New()
		user     = common.HexToAddress("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b")
		addr     = common.HexToAddress("0x1234")
	)
	dbHandle := instance.OpenMemoryDB()
	_, stateHandle := instance.StateOpen(StateParams{
		DatabaseParams: DatabaseParams{
			dbHandle,
		},
		Root: test.EmptyHash,
	})
	_, statedb := instance.statedbs.Get(stateHandle)

	// deploy test contract
	_, resultDeploy := instance.EvmApply(EvmParams{
		HandleParams: HandleParams{Handle: stateHandle},
		From:         user,
		To:           nil,
		Input:        test.StorageContractDeploy(common.Big0),
		AvailableGas: 200000,
	})
	if resultDeploy.EvmError != "" {
		t.Fatalf("vm error: %v", resultDeploy.EvmError)
	}

	// add some test balance
	statedb.AddBalance(user, big.NewInt(1000))

	checks := []struct {
		name         string
		err          string
		shouldRevert bool
		params       EvmParams
	}{
		{
			name: "EOA transfer with insufficient balance",
			err:  "insufficient balance for transfer",
			params: EvmParams{
				HandleParams: HandleParams{Handle: stateHandle},
				From:         user,
				To:           &addr,
				Value:        (*hexutil.Big)(big.NewInt(1001)),
				AvailableGas: 100,
			},
		},
		{
			name: "contract deployment with insufficient gas for execution",
			err:  "out of gas",
			params: EvmParams{
				HandleParams: HandleParams{Handle: stateHandle},
				From:         user,
				Input:        test.StorageContractDeploy(common.Big0),
				AvailableGas: 123,
			},
		},
		{
			name: "contract deployment with insufficient gas for code storage",
			err:  "contract creation code storage out of gas",
			params: EvmParams{
				HandleParams: HandleParams{Handle: stateHandle},
				From:         user,
				Input:        test.StorageContractDeploy(common.Big0),
				AvailableGas: 50000,
			},
		},
		{
			// illegal because the constructor of this contract does not accept funds
			name:         "contract deployment with illegal value transfer",
			err:          "execution reverted",
			shouldRevert: true,
			params: EvmParams{
				HandleParams: HandleParams{Handle: stateHandle},
				From:         user,
				Value:        (*hexutil.Big)(big.NewInt(100)),
				Input:        test.StorageContractDeploy(common.Big0),
				AvailableGas: 200000,
			},
		},
		{
			name:         "contract call to unknown function",
			err:          "execution reverted",
			shouldRevert: true,
			params: EvmParams{
				HandleParams: HandleParams{Handle: stateHandle},
				From:         user,
				To:           resultDeploy.ContractAddress,
				Input:        common.FromHex("01020304"),
				AvailableGas: 200000,
			},
		},
		{
			name: "contract call with insufficient gas",
			err:  "out of gas",
			params: EvmParams{
				HandleParams: HandleParams{Handle: stateHandle},
				From:         user,
				To:           resultDeploy.ContractAddress,
				Input:        test.StorageContractStore(common.Big3),
				AvailableGas: 2000,
			},
		},
	}

	for i, check := range checks {
		t.Run(check.name, func(t *testing.T) {
			// make sure the nonce is increased between calls, otherwise there might be contract address collisions
			statedb.SetNonce(user, uint64(i+1))
			err, result := instance.EvmApply(check.params)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if result.EvmError != check.err {
				t.Fatalf("unexpected EvmError: expected \"%v\" actual \"%v\"", check.err, result.EvmError)
			}
		})
	}
}
