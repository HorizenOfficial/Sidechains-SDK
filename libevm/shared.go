//go:build cgo

package main

// #cgo CFLAGS: -g -Wall -O3 -fpic -Werror
// #include <stdlib.h>
import "C"
import (
	"encoding/json"
	"errors"
	"github.com/ethereum/go-ethereum/log"
	"libevm/lib"
	"reflect"
	"unsafe"
)

// instance holds the initialized lib.Service
var instance *lib.Service

type InitializeParams struct {
	Path string `json:"path"`
}

type InteropResult struct {
	Error  string      `json:"error"`
	Result interface{} `json:"result"`
}

func initialize(params InitializeParams) error {
	if instance != nil {
		_ = instance.Close()
		instance = nil
	}
	err, newInstance := lib.InitWithLevelDB(params.Path)
	if err == nil {
		instance = newInstance
	}
	return err
}

func response(err error, result interface{}) *C.char {
	var response InteropResult
	if err != nil {
		response.Error = err.Error()
	} else {
		response.Result = result
	}
	jsonBytes, err := json.Marshal(response)
	if err != nil {
		log.Error("unable to marshal response", "error", err)
		return nil
	}
	jsonString := string(jsonBytes)
	log.Debug("<<", "json", jsonString)
	return C.CString(jsonString)
}

//export Free
func Free(ptr unsafe.Pointer) {
	C.free(ptr)
}

//export Invoke
func Invoke(method *C.char, args *C.char) *C.char {
	return response(invoke(C.GoString(method), C.GoString(args)))
}

func invoke(method string, args string) (error, interface{}) {
	log.Info(">>", "method", method, "args", args)
	var f reflect.Value
	if method == "Initialize" {
		// initialize is special as it is not a member function of lib.Service
		f = reflect.ValueOf(initialize)
	} else {
		if instance == nil {
			return errors.New("not initialized"), nil
		}
		// find the target function
		f = reflect.ValueOf(instance).MethodByName(method)
	}
	if !f.IsValid() {
		return errors.New("method not found"), nil
	}
	// unmarshal args struct
	var inputs []reflect.Value
	if f.Type().NumIn() > 0 {
		v := reflect.New(f.Type().In(0))
		err := json.Unmarshal([]byte(args), v.Interface())
		if err != nil {
			return err, nil
		}
		inputs = append(inputs, v.Elem())
	}
	// call method
	results := f.Call(inputs)
	// check if the first return value is an error
	errorInterface := reflect.TypeOf((*error)(nil)).Elem()
	canError := f.Type().NumOut() > 0 && f.Type().Out(0).Implements(errorInterface)
	if canError && !results[0].IsNil() {
		return results[0].Interface().(error), nil
	}
	// return results if any
	if f.Type().NumOut() > 1 && results[1].CanInterface() {
		return nil, results[1].Interface()
	}
	return nil, nil
}

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
