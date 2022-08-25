package interop

import (
	_ "embed"
	"encoding/json"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"libevm/lib"
	"math/big"
	"testing"
)

//go:generate solc --bin --hashes --opcodes --storage-layout --optimize -o compiled --overwrite ../contracts/Storage.sol
var (
	//go:embed compiled/Storage.bin
	storageContractDeploy string
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
	t.Log("invoke", method, jsonArgs)
	err, result := callMethod(instance, method, jsonArgs)
	if err != nil {
		t.Errorf("invocation failed: %v", err)
	}
	t.Log("response", toJsonResponse(err, result))
	return result
}

func TestInvoke(t *testing.T) {
	var (
		instance     = lib.New()
		user         = common.HexToAddress("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b")
		emptyHash    = common.HexToHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
		initialValue = common.Big0
		anotherValue = big.NewInt(5555)
		funcStore    = "6057361d"
		funcRetrieve = "2e64cec1"
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
		Input:        append(common.Hex2Bytes(storageContractDeploy), common.BigToHash(initialValue).Bytes()...),
		GasLimit:     200000,
		GasPrice:     (*hexutil.Big)(big.NewInt(1000000000)),
	}).(*lib.EvmResult)
	if result.EvmError != "" {
		t.Fatalf("vm error: %v", result.EvmError)
	}
	getCodeResult := call(t, instance, "StateGetCode", lib.AccountParams{
		HandleParams: lib.HandleParams{Handle: handle},
		Address:      *result.ContractAddress,
	}).([]byte)
	const expectedCode = "60806040526004361060305760003560e01c80632e64cec1146035578063371303c01460565780636057361d14606a575b600080fd5b348015604057600080fd5b5060005460405190815260200160405180910390f35b348015606157600080fd5b506068607a565b005b606860753660046086565b600055565b6000546075906001609e565b600060208284031215609757600080fd5b5035919050565b8082018082111560be57634e487b7160e01b600052601160045260246000fd5b9291505056fea26469706673582212205b989fe38f3c1c7022e6705c5e79a5d2fc589594d6a6075c784b1d171f60832c64736f6c63430008100033"
	if expectedCode != common.Bytes2Hex(getCodeResult) {
		// note: this depends on the version of the currently installed Solidity compiler, skip this for now
		//t.Fatalf("deployed code does not match %s", common.Bytes2Hex(getCodeResult))
	}
	// call function to store value
	call(t, instance, "EvmApply", lib.EvmParams{
		HandleParams: lib.HandleParams{Handle: handle},
		From:         user,
		To:           result.ContractAddress,
		Input:        append(common.Hex2Bytes(funcStore), common.BigToHash(anotherValue).Bytes()...),
		GasLimit:     200000,
		GasPrice:     (*hexutil.Big)(big.NewInt(1000000000)),
	})
	// call function to retrieve value
	resultRetrieve := call(t, instance, "EvmApply", lib.EvmParams{
		HandleParams: lib.HandleParams{Handle: handle},
		From:         user,
		To:           result.ContractAddress,
		Input:        common.Hex2Bytes(funcRetrieve),
		GasLimit:     200000,
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
