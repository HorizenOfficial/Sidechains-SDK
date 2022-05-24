//go:build cgo

package main

// #cgo CFLAGS: -g -Wall -O3 -fpic -Werror
// #include <stdlib.h>
import "C"
import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/vm/runtime"
	"libevm/evm"
	"libevm/helper"
	"math/big"
	"unsafe"
)

// SerializableConfig mirrors configurable parts of the runtime.Config
type SerializableConfig struct {
	Difficulty  helper.BigInt  `json:"difficulty"`
	Origin      common.Address `json:"origin"`
	Coinbase    common.Address `json:"coinbase"`
	BlockNumber helper.BigInt  `json:"blockNumber"`
	Time        helper.BigInt  `json:"time"`
	GasLimit    uint64         `json:"gasLimit"`
	GasPrice    helper.BigInt  `json:"gasPrice"`
	Value       helper.BigInt  `json:"value"`
	BaseFee     helper.BigInt  `json:"baseFee"`
}

// Map SerializableConfig to runtime.Config
func (c *SerializableConfig) getConfig() *runtime.Config {
	return &runtime.Config{
		ChainConfig: nil,
		Difficulty:  c.Difficulty.Int,
		Origin:      c.Origin,
		Coinbase:    c.Coinbase,
		BlockNumber: c.BlockNumber.Int,
		Time:        c.Time.Int,
		GasLimit:    c.GasLimit,
		GasPrice:    c.GasPrice.Int,
		Value:       c.Value.Int,
		BaseFee:     c.BaseFee.Int,
	}
}

type StateRootResult struct {
	InteropResult
	StateRoot common.Hash `json:"stateRoot"`
}

type CreateParams struct {
	SerializableConfig
	Input        []byte `json:"input"`
	DiscardState bool   `json:"discardState"`
}

type CreateResult struct {
	InteropResult
	Address     common.Address `json:"address"`
	LeftOverGas uint64         `json:"leftOverGas"`
}

type CallParams struct {
	SerializableConfig
	Address      common.Address `json:"address"`
	Input        []byte         `json:"input"`
	DiscardState bool           `json:"discardState"`
}

type CallResult struct {
	InteropResult
	Ret         []byte `json:"ret"`
	LeftOverGas uint64 `json:"leftOverGas"`
}

type BalanceParams struct {
	Address common.Address `json:"address"`
	Value   *helper.BigInt `json:"value"`
}

// instance holds a local EvmService
var instance *evm.Instance

//export Free
func Free(ptr unsafe.Pointer) {
	C.free(ptr)
}

//export Initialize
func Initialize(path *C.char) *C.char {
	if instance != nil {
		_ = instance.Close()
		instance = nil
	}
	newInstance, err := evm.InitWithLevelDB(C.GoString(path), "zen/evm/db/", 0, 0)
	if err == nil {
		instance = newInstance
	}
	return toJava(Result(err))
}

//export SetStateRoot
func SetStateRoot(stateRootHex *C.char) *C.char {
	stateRoot := common.HexToHash(C.GoString(stateRootHex))
	err := instance.SetStateRoot(stateRoot)
	return toJava(Result(err))
}

//export GetIntermediateStateRoot
func GetIntermediateStateRoot() *C.char {
	result := StateRootResult{
		StateRoot: instance.IntermediateRoot(),
	}
	return toJava(&result)
}

//export CommitState
func CommitState() *C.char {
	stateRoot, err := instance.Commit()
	if err != nil {
		return toJava(Fail(err))
	}
	result := StateRootResult{StateRoot: stateRoot}
	return toJava(&result)
}

//export ContractCreate
func ContractCreate(args *C.char) *C.char {
	var params CreateParams
	err := fromJava(args, &params)
	if err != nil {
		return toJava(Fail(err))
	}
	_, addr, leftOverGas, err := instance.Create(params.Input, params.getConfig(), params.DiscardState)
	if err != nil {
		return toJava(Fail(err))
	}
	result := CreateResult{
		Address:     addr,
		LeftOverGas: leftOverGas,
	}
	return toJava(&result)
}

//export ContractCall
func ContractCall(args *C.char) *C.char {
	var params CallParams
	err := fromJava(args, &params)
	if err != nil {
		return toJava(Fail(err))
	}
	ret, leftOverGas, err := instance.Call(params.Address, params.Input, params.getConfig(), params.DiscardState)
	if err != nil {
		return toJava(Fail(err))
	}
	result := CallResult{
		Ret:         ret,
		LeftOverGas: leftOverGas,
	}
	return toJava(&result)
}

func updateBalance(args *C.char, f func(common.Address, *big.Int)) *C.char {
	var params BalanceParams
	err := fromJava(args, &params)
	if err != nil {
		return toJava(Fail(err))
	}
	f(params.Address, params.Value.Int)
	return toJava(Success())
}

//export SetBalance
func SetBalance(args *C.char) *C.char {
	return updateBalance(args, instance.SetBalance)
}

//export AddBalance
func AddBalance(args *C.char) *C.char {
	return updateBalance(args, instance.AddBalance)
}

//export SubBalance
func SubBalance(args *C.char) *C.char {
	return updateBalance(args, instance.SubBalance)
}
