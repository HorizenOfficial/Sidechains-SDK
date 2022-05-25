//go:build cgo

package main

import "C"
import (
	"encoding/json"
	"github.com/ethereum/go-ethereum/log"
)

type InteropResult struct {
	Error  string
	Result interface{}
}

func toJava(err error, result interface{}) *C.char {
	var response InteropResult
	if err != nil {
		response.Error = err.Error()
	} else {
		response.Result = result
	}
	ret, err := json.Marshal(response)
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
