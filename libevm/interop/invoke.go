package interop

import (
	"encoding/json"
	"fmt"
	"github.com/ethereum/go-ethereum/log"
	"libevm/lib"
	"reflect"
)

// instance holds a singleton of lib.Service
var instance = lib.New()

func Invoke(method string, args string) string {
	log.Debug(">> invoke", "method", method, "args", args)
	result := toJsonResponse(callMethod(instance, method, args))
	log.Debug("<< response", "result", result)
	return result
}

func callMethod(target interface{}, method string, args string) (error, interface{}) {
	// find the target function
	f := reflect.ValueOf(target).MethodByName(method)
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

func toJsonResponse(err error, result interface{}) string {
	var res struct {
		Error  string      `json:"error"`
		Result interface{} `json:"result"`
	}
	if err != nil {
		res.Error = err.Error()
	} else {
		res.Result = result
	}
	bytes, marshalErr := json.Marshal(res)
	if marshalErr != nil {
		log.Error("unable to marshal response", "marshalErr", marshalErr, "response", res)
		return ""
	}
	return string(bytes)
}
