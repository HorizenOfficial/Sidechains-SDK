//go:build cgo

package main

// #cgo CFLAGS: -g -Wall -O3 -fpic -Werror
// #include <stdlib.h>
import "C"
import (
	"encoding/json"
	"fmt"
	"github.com/ethereum/go-ethereum/log"
	"libevm/lib"
	"reflect"
	"unsafe"
)

// instance holds a singleton of lib.Service
var instance = lib.New()

type InteropResult struct {
	Error  string      `json:"error"`
	Result interface{} `json:"result"`
}

//export Free
func Free(ptr unsafe.Pointer) {
	C.free(ptr)
}

//export Invoke
func Invoke(method *C.char, args *C.char) *C.char {
	err, result := invoke(C.GoString(method), C.GoString(args))
	var response InteropResult
	if err != nil {
		response.Error = err.Error()
	} else {
		response.Result = result
	}
	jsonBytes, marshalErr := json.Marshal(response)
	if marshalErr != nil {
		log.Error("unable to marshal response", "error", marshalErr)
		return nil
	}
	jsonString := string(jsonBytes)
	log.Debug("<< response", "err", err, "result", jsonString)
	return C.CString(jsonString)
}

func invoke(method string, args string) (error, interface{}) {
	log.Debug(">> invoke", "method", method, "args", args)
	// find the target function
	f := reflect.ValueOf(instance).MethodByName(method)
	if !f.IsValid() {
		return fmt.Errorf("method not found: %s", method), nil
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
