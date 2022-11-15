package interop

import (
	"encoding/json"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"libevm/lib"
	"libevm/test"
	"math/big"
	"testing"
)

func call(t *testing.T, instance *lib.Service, method string, args interface{}) interface{} {
	jsonArgs := ""
	if args != nil {
		jsonBytes, err := json.Marshal(args)
		if err != nil {
			panic(err)
		}
		jsonArgs = string(jsonBytes)
	}
	//t.Log("invoke", method, jsonArgs)
	err, result := callMethod(instance, method, jsonArgs)
	if err != nil {
		t.Errorf("invocation failed: %v", err)
	}
	//t.Log("response", toJsonResponse(err, result))
	return result
}

func TestInvoke(t *testing.T) {
	var (
		instance     = lib.New()
		user         = common.HexToAddress("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b")
		emptyHash    = common.HexToHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
		initialValue = common.Big0
		anotherValue = big.NewInt(5555)
	)

	dbHandle := call(t, instance, "OpenLevelDB", lib.LevelDBParams{Path: t.TempDir()}).(int)
	handle := call(t, instance, "StateOpen", lib.StateParams{
		DatabaseParams: lib.DatabaseParams{DatabaseHandle: dbHandle},
		Root:           emptyHash,
	}).(int)
	call(t, instance, "StateAddBalance", lib.BalanceParams{
		AccountParams: lib.AccountParams{
			HandleParams: lib.HandleParams{Handle: handle},
			Address:      user,
		},
		Amount: (*hexutil.Big)(big.NewInt(1000000000000000000)),
	})
	call(t, instance, "StateSetNonce", lib.NonceParams{
		AccountParams: lib.AccountParams{
			HandleParams: lib.HandleParams{Handle: handle},
			Address:      user,
		},
		Nonce: 1,
	})
	// deploy contract
	result := call(t, instance, "EvmApply", lib.EvmParams{
		HandleParams: lib.HandleParams{Handle: handle},
		From:         user,
		To:           nil,
		Input:        test.StorageContractDeploy(initialValue),
		AvailableGas: 200000,
		GasPrice:     (*hexutil.Big)(big.NewInt(1000000000)),
	}).(*lib.EvmResult)
	if result.EvmError != "" {
		t.Fatalf("vm error: %v", result.EvmError)
	}
	getCodeResult := call(t, instance, "StateGetCode", lib.AccountParams{
		HandleParams: lib.HandleParams{Handle: handle},
		Address:      *result.ContractAddress,
	}).([]byte)
	if common.Bytes2Hex(test.StorageContractRuntimeCode()) != common.Bytes2Hex(getCodeResult) {
		t.Fatalf("deployed code does not match %s", common.Bytes2Hex(getCodeResult))
	}
	// call function to store value
	call(t, instance, "EvmApply", lib.EvmParams{
		HandleParams: lib.HandleParams{Handle: handle},
		From:         user,
		To:           result.ContractAddress,
		Input:        test.StorageContractStore(anotherValue),
		AvailableGas: 200000,
		GasPrice:     (*hexutil.Big)(big.NewInt(1000000000)),
	})
	// call function to retrieve value
	resultRetrieve := call(t, instance, "EvmApply", lib.EvmParams{
		HandleParams: lib.HandleParams{Handle: handle},
		From:         user,
		To:           result.ContractAddress,
		Input:        test.StorageContractRetrieve(),
		AvailableGas: 200000,
		GasPrice:     (*hexutil.Big)(big.NewInt(1000000000)),
	}).(*lib.EvmResult)
	if resultRetrieve.EvmError != "" {
		t.Fatalf("vm error: %v", resultRetrieve.EvmError)
	}
	retrievedValue := common.BytesToHash(resultRetrieve.ReturnData).Big()
	if anotherValue.Cmp(retrievedValue) != 0 {
		t.Fatalf("retrieved bad value: expected %v, actual %v", anotherValue, retrievedValue)
	}
	// verify that EOA nonce was not updated
	nonce := call(t, instance, "StateGetNonce", lib.AccountParams{
		HandleParams: lib.HandleParams{Handle: handle},
		Address:      user,
	}).(hexutil.Uint64)
	if uint64(nonce) != 1 {
		t.Fatalf("nonce was modified: expected 0, actual %v", nonce)
	}
	// cleanup
	call(t, instance, "CloseDatabase", lib.DatabaseParams{
		DatabaseHandle: dbHandle,
	})
}
