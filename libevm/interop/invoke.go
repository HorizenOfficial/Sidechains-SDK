package interop

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/ethereum/go-ethereum/log"
	"reflect"
)

var errorInterfaceType = reflect.TypeOf((*error)(nil)).Elem()

var (
	ErrMethodNotFound   = errors.New("method not found")
	ErrInvalidArguments = errors.New("invalid arguments")
	ErrInvocationError  = errors.New("invocation error")
)

func Invoke(target interface{}, method string, args string) string {
	log.Trace(">> invoke", "method", method, "args", args)
	result := toJsonResponse(callMethod(target, method, args))
	log.Trace("<< response", "result", result)
	return result
}

// callMethod calls the given method on the target, optionally passing args by unmarshalling json to the required type
// functions that are called this way need to fulfill the following requirements:
// 1. have zero or one parameter
// 2. if there is a parameter the given args must json-unmarshal to the type of the parameter
// 3. have at most two return values
// 4. if there is one return value it must be either an error type or a return value that can be marshalled to json
// 5. if there are two return values the first one must be an error type and the second must be a return value that can be marshalled to json
func callMethod(target interface{}, method string, args string) (error, interface{}) {
	// find the target function
	fun := reflect.ValueOf(target).MethodByName(method)
	if !fun.IsValid() {
		return fmt.Errorf("%w: %s", ErrMethodNotFound, method), nil
	}
	var (
		funType    = fun.Type()
		funInputs  = funType.NumIn()
		funOutputs = funType.NumOut()
		canError   = false
		// unmarshalled args
		inputs []reflect.Value
	)
	// validate inputs
	switch funInputs {
	case 0:
		if args != "" {
			return fmt.Errorf("%w: function %s has no arguments, but was called with: %s", ErrInvalidArguments, method, args), nil
		}
	case 1:
		if args == "" {
			return fmt.Errorf("%w: function %s must be called with an argument", ErrInvalidArguments, method), nil
		}
		// unmarshal args to the type of the one parameter of the function
		dec := json.NewDecoder(bytes.NewReader([]byte(args)))
		// make sure to throw errors incase unknown fields are passed, do not silently ignore this
		// as it is most likely a sign of buggy interface code
		dec.DisallowUnknownFields()
		argsType := reflect.New(funType.In(0))
		err := dec.Decode(argsType.Interface())
		if err != nil {
			return fmt.Errorf("%w: %v", ErrInvalidArguments, err), nil
		}
		inputs = append(inputs, argsType.Elem())
	default:
		return fmt.Errorf("%w: functions must have zero or one argument, but the function %s has %d arguments", ErrInvocationError, method, funInputs), nil
	}
	// validate outputs
	switch funOutputs {
	case 0:
		// no return values, nothing to validate
	case 1, 2:
		// check if the first return value is an error
		canError = funType.Out(0).Implements(errorInterfaceType)
		if funOutputs == 2 && !canError {
			return fmt.Errorf("%w: functions with two return values must have an error type as the first one, but the function %s has two non-error return values", ErrInvocationError, method), nil
		}
	default:
		return fmt.Errorf("%w: functions must have two or less return values, but the function %s has %d return values", ErrInvocationError, method, funOutputs), nil
	}
	// call method
	results := fun.Call(inputs)
	// return error if any
	if canError && !results[0].IsNil() {
		return results[0].Interface().(error), nil
	}
	// return result if any
	returnValueIndex := 0
	if canError {
		returnValueIndex = 1
	}
	if funOutputs > returnValueIndex && results[returnValueIndex].CanInterface() {
		return nil, results[returnValueIndex].Interface()
	}
	// no error, no result
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
	jsonBytes, marshalErr := json.Marshal(res)
	if marshalErr != nil {
		log.Error("unable to marshal response", "marshalErr", marshalErr, "response", res)
		return ""
	}
	return string(jsonBytes)
}
