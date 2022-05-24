//go:build cgo

package main

import "C"
import (
	"encoding/json"
	"github.com/ethereum/go-ethereum/log"
)

type InteropResult struct {
	Code    uint8  `json:"code"`
	Message string `json:"message"`
}

func Success() *InteropResult {
	return &InteropResult{
		Code:    0,
		Message: "",
	}
}

func Fail(err error) *InteropResult {
	return &InteropResult{
		Code:    1,
		Message: err.Error(),
	}
}

func Result(err error) *InteropResult {
	if err == nil {
		return Success()
	} else {
		return Fail(err)
	}
}

func toJava(obj interface{}) *C.char {
	ret, err := json.Marshal(obj)
	if err != nil {
		log.Error("unable to marshal response", "error", err)
		return nil
	}
	log.Debug("GO->JAVA", "json", string(ret))
	return C.CString(string(ret))
}

func fromJava(str *C.char, obj interface{}) error {
	log.Debug("JAVA->GO", "json", C.GoString(str))
	err := json.Unmarshal([]byte(C.GoString(str)), &obj)
	if err != nil {
		log.Error("unable to unmarshal params", "error", err)
	}
	return err
}
