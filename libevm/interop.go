//go:build cgo

package main

import "C"
import (
	"encoding/json"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/vm/runtime"
	"github.com/ethereum/go-ethereum/log"
	"libevm/helper"
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
