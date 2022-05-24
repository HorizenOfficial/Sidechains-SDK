//go:build cgo

package main

// #cgo CFLAGS: -g -Wall -O3 -fpic -Werror
// #include <stdlib.h>
import "C"
import (
	"github.com/ethereum/go-ethereum/common"
	"libevm/evm"
	"libevm/types"
	"unsafe"
)

type HandleResult struct {
	InteropResult
	Handle int `json:"handle"`
}

type StateRootResult struct {
	InteropResult
	StateRoot common.Hash `json:"stateRoot"`
}

type CreateParams struct {
	types.SerializableConfig
	Input        []byte `json:"input"`
	DiscardState bool   `json:"discardState"`
}

type CreateResult struct {
	InteropResult
	Address     common.Address `json:"address"`
	LeftOverGas uint64         `json:"leftOverGas"`
}

type CallParams struct {
	types.SerializableConfig
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
	Value   *types.BigInt  `json:"value"`
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
	newInstance, err := evm.InitWithLevelDB(C.GoString(path))
	if err == nil {
		instance = newInstance
	}
	return toJava(Result(err))
}

//export OpenState
func OpenState(stateRootHex *C.char) *C.char {
	root := common.HexToHash(C.GoString(stateRootHex))
	handle, err := instance.OpenState(root)
	if err != nil {
		return toJava(Fail(err))
	}
	result := HandleResult{
		Handle: handle,
	}
	return toJava(&result)
}

//export CloseState
func CloseState(handle int) *C.char {
	instance.CloseState(handle)
	return toJava(Success())
}

//export GetIntermediateStateRoot
func GetIntermediateStateRoot(handle int) *C.char {
	statedb, err := instance.GetState(handle)
	if err != nil {
		return toJava(Fail(err))
	}
	result := StateRootResult{
		StateRoot: statedb.IntermediateRoot(true),
	}
	return toJava(&result)
}

////export CommitState
//func CommitState() *C.char {
//	stateRoot, err := instance.Commit()
//	if err != nil {
//		return toJava(Fail(err))
//	}
//	result := StateRootResult{StateRoot: stateRoot}
//	return toJava(&result)
//}
//
////export ContractCreate
//func ContractCreate(args *C.char) *C.char {
//	var params CreateParams
//	err := fromJava(args, &params)
//	if err != nil {
//		return toJava(Fail(err))
//	}
//	_, addr, leftOverGas, err := instance.Create(params.Input, params.getConfig(), params.DiscardState)
//	if err != nil {
//		return toJava(Fail(err))
//	}
//	result := CreateResult{
//		Address:     addr,
//		LeftOverGas: leftOverGas,
//	}
//	return toJava(&result)
//}
//
////export ContractCall
//func ContractCall(args *C.char) *C.char {
//	var params CallParams
//	err := fromJava(args, &params)
//	if err != nil {
//		return toJava(Fail(err))
//	}
//	ret, leftOverGas, err := instance.Call(params.Address, params.Input, params.getConfig(), params.DiscardState)
//	if err != nil {
//		return toJava(Fail(err))
//	}
//	result := CallResult{
//		Ret:         ret,
//		LeftOverGas: leftOverGas,
//	}
//	return toJava(&result)
//}
//
//func updateBalance(args *C.char, f func(common.Address, *big.Int)) *C.char {
//	var params BalanceParams
//	err := fromJava(args, &params)
//	if err != nil {
//		return toJava(Fail(err))
//	}
//	f(params.Address, params.Value.Int)
//	return toJava(Success())
//}
//
////export SetBalance
//func SetBalance(args *C.char) *C.char {
//	return updateBalance(args, instance.SetBalance)
//}
//
////export AddBalance
//func AddBalance(args *C.char) *C.char {
//	return updateBalance(args, instance.AddBalance)
//}
//
////export SubBalance
//func SubBalance(args *C.char) *C.char {
//	return updateBalance(args, instance.SubBalance)
//}
