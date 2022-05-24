package evm

import (
	_ "embed"
	"github.com/ethereum/go-ethereum/common"
	"testing"
)

//go:generate solc --bin --hashes --opcodes --storage-layout --optimize -o contracts/compiled --overwrite contracts/Storage.sol
var (
	//go:embed contracts/compiled/Storage.bin
	storageContractDeploy string
)

const (
	storageContractCallInc      = "371303c0"
	storageContractCallRetrieve = "2e64cec1"
	storageContractCallStore    = "6057361d"
)

func merge(code string, params ...[]byte) []byte {
	input := common.FromHex(code)
	for _, param := range params {
		input = append(input, param...)
	}
	return input
}

func TestLevelDbPersistence(t *testing.T) {
	instance, err := InitWithLevelDB(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	err = instance.Close()
	if err != nil {
		t.Fatal("unable to close database", err)
	}
	// TODO: implement level-db persistence tests
	//origin := common.HexToAddress("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b")
	// do something
	// commit and get new root hash
	// close
	// reopen state at root hash
	// do something to verify previous changes are still existing
	// close
}

//func TestEvmTransactions(t *testing.T) {
//	origin := common.HexToAddress("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b")
//	instance, err := InitWithMemoryDB()
//	if err != nil {
//		panic(err)
//	}
//
//	config := &runtime.Config{
//		Origin:    origin,
//		GasLimit:  100000,
//		Value:     big.NewInt(0),
//		EVMConfig: vm.Config{},
//	}
//	instance.AddBalance(origin, big.NewInt(1))
//	value := uint256.NewInt(0x1234)
//
//	_, contractAddress, leftOverGas, err := instance.Create(merge(storageContractDeploy, value.PaddedBytes(32)), config, true)
//	if err != nil {
//		t.Error("test deploy of contract failed", err)
//	} else {
//		t.Log("test deploy result", "leftOverGas", leftOverGas, "contractAddress", contractAddress)
//	}
//
//	_, contractAddress, leftOverGas, err = instance.Create(merge(storageContractDeploy, value.PaddedBytes(32)), config, false)
//	if err != nil {
//		t.Error("deploy of contract failed", err)
//	} else {
//		t.Log("deploy result", "leftOverGas", leftOverGas, "contractAddress", contractAddress)
//	}
//
//	_, leftOverGas, err = instance.Call(contractAddress, merge(storageContractCallInc), config, false)
//	if err != nil {
//		t.Error("deploy of contract failed", err)
//	} else {
//		t.Log("call result", "leftOverGas", leftOverGas, "function", "inc()")
//	}
//
//	ret, leftOverGas, err := instance.Call(contractAddress, merge(storageContractCallRetrieve), config, false)
//	if err != nil {
//		t.Error("deploy of contract call", err)
//	} else {
//		value.SetBytes32(ret)
//		t.Log("call result", "leftOverGas", leftOverGas, "function", "retrieve()", "value", value)
//	}
//
//	config.Value = big.NewInt(1)
//	value = uint256.NewInt(0x2222)
//	_, leftOverGas, err = instance.Call(contractAddress, merge(storageContractCallStore, value.PaddedBytes(32)), config, false)
//	if err != nil {
//		t.Error("deploy of contract call", err)
//	} else {
//		t.Log("call result", "leftOverGas", leftOverGas, "function", "store()", "value", value)
//	}
//
//	config.Value = big.NewInt(0)
//	ret, leftOverGas, err = instance.Call(contractAddress, merge(storageContractCallRetrieve), config, false)
//	if err != nil {
//		t.Error("deploy of contract call", err)
//	} else {
//		value.SetBytes32(ret)
//		t.Log("call result", "leftOverGas", leftOverGas, "function", "retrieve()", "value", value)
//	}
//
//	// get contract state trie
//	//stateObject := statedb.GetOrNewStateObject(contractAddress)
//	//_ = rawdb.InspectDatabase(instance.storage, []byte{}, []byte{})
//
//	err = instance.Close()
//	if err != nil {
//		t.Error("unable to close database", err)
//	}
//}
