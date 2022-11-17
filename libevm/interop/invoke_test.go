package interop

import (
	"encoding/json"
	"errors"
	"fmt"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"libevm/lib"
	"libevm/test"
	"math/big"
	"reflect"
	"testing"
)

func call(t *testing.T, instance *lib.Service, method string, args interface{}) interface{} {
	jsonArgs := ""
	if args != nil {
		jsonBytes, err := json.Marshal(args)
		if err != nil {
			panic(err)
		}
		jsonArgs = string(jsonBytes)
	}
	//t.Log("invoke", method, jsonArgs)
	err, result := callMethod(instance, method, jsonArgs)
	if err != nil {
		t.Errorf("invocation failed: %v", err)
	}
	//t.Log("response", toJsonResponse(err, result))
	return result
}

func TestInvoke(t *testing.T) {
	var (
		instance     = lib.New()
		user         = common.HexToAddress("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b")
		emptyHash    = common.HexToHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
		initialValue = common.Big0
		anotherValue = big.NewInt(5555)
	)

	dbHandle := call(t, instance, "OpenLevelDB", lib.LevelDBParams{Path: t.TempDir()}).(int)
	handle := call(t, instance, "StateOpen", lib.StateParams{
		DatabaseParams: lib.DatabaseParams{DatabaseHandle: dbHandle},
		Root:           emptyHash,
	}).(int)
	call(t, instance, "StateAddBalance", lib.BalanceParams{
		AccountParams: lib.AccountParams{
			HandleParams: lib.HandleParams{Handle: handle},
			Address:      user,
		},
		Amount: (*hexutil.Big)(big.NewInt(1000000000000000000)),
	})
	call(t, instance, "StateSetNonce", lib.NonceParams{
		AccountParams: lib.AccountParams{
			HandleParams: lib.HandleParams{Handle: handle},
			Address:      user,
		},
		Nonce: 1,
	})
	// deploy contract
	result := call(t, instance, "EvmApply", lib.EvmParams{
		HandleParams: lib.HandleParams{Handle: handle},
		From:         user,
		To:           nil,
		Input:        test.StorageContractDeploy(initialValue),
		AvailableGas: 200000,
		GasPrice:     (hexutil.Big)(*big.NewInt(1000000000)),
	}).(*lib.EvmResult)
	if result.EvmError != "" {
		t.Fatalf("vm error: %v", result.EvmError)
	}
	getCodeResult := call(t, instance, "StateGetCode", lib.AccountParams{
		HandleParams: lib.HandleParams{Handle: handle},
		Address:      *result.ContractAddress,
	}).([]byte)
	if common.Bytes2Hex(test.StorageContractRuntimeCode()) != common.Bytes2Hex(getCodeResult) {
		t.Fatalf("deployed code does not match %s", common.Bytes2Hex(getCodeResult))
	}
	// call function to store value
	call(t, instance, "EvmApply", lib.EvmParams{
		HandleParams: lib.HandleParams{Handle: handle},
		From:         user,
		To:           result.ContractAddress,
		Input:        test.StorageContractStore(anotherValue),
		AvailableGas: 200000,
		GasPrice:     (hexutil.Big)(*big.NewInt(1000000000)),
	})
	// call function to retrieve value
	resultRetrieve := call(t, instance, "EvmApply", lib.EvmParams{
		HandleParams: lib.HandleParams{Handle: handle},
		From:         user,
		To:           result.ContractAddress,
		Input:        test.StorageContractRetrieve(),
		AvailableGas: 200000,
		GasPrice:     (hexutil.Big)(*big.NewInt(1000000000)),
	}).(*lib.EvmResult)
	if resultRetrieve.EvmError != "" {
		t.Fatalf("vm error: %v", resultRetrieve.EvmError)
	}
	retrievedValue := common.BytesToHash(resultRetrieve.ReturnData).Big()
	if anotherValue.Cmp(retrievedValue) != 0 {
		t.Fatalf("retrieved bad value: expected %v, actual %v", anotherValue, retrievedValue)
	}
	// verify that EOA nonce was not updated
	nonce := call(t, instance, "StateGetNonce", lib.AccountParams{
		HandleParams: lib.HandleParams{Handle: handle},
		Address:      user,
	}).(hexutil.Uint64)
	if uint64(nonce) != 1 {
		t.Fatalf("nonce was modified: expected 0, actual %v", nonce)
	}
	// cleanup
	call(t, instance, "CloseDatabase", lib.DatabaseParams{
		DatabaseHandle: dbHandle,
	})
}

type MockLibrary struct{}

var MockError = errors.New("mock error")

type MockParams struct {
	Foo    int         `json:"foo"`
	Bar    string      `json:"bar"`
	Nested *MockParams `json:"nested"`
}

func (m *MockLibrary) NoParam()                                   {}
func (m *MockLibrary) NoParamResult() string                      { return "toot gaya" }
func (m *MockLibrary) NoParamNilError() error                     { return nil }
func (m *MockLibrary) NoParamError() error                        { return fmt.Errorf("%w: kaputt", MockError) }
func (m *MockLibrary) NoParamBadErrorReturn() (string, error)     { return "", nil /* invalid */ }
func (m *MockLibrary) NoParamTwoResults() (string, string)        { return "", "" /* invalid */ }
func (m *MockLibrary) OneParam(a int)                             {}
func (m *MockLibrary) OneParamEcho(str string) string             { return str }
func (m *MockLibrary) TwoParams(a int, b int)                     { /* invalid: more than one parameter */ }
func (m *MockLibrary) ComplexParam(params MockParams) *MockParams { return params.Nested }
func (m *MockLibrary) ArrayParam(params []int) int                { return len(params) }
func (m *MockLibrary) ConditionalErrorNoResult(fail int) error {
	if fail != 0 {
		return fmt.Errorf("%w: kaputt %v", MockError, fail)
	}
	return nil
}
func (m *MockLibrary) ConditionalErrorWithResult(nr int) (error, string) {
	if nr == 7 {
		return fmt.Errorf("%w: oh noes", MockError), ""
	}
	return nil, "success"
}

func TestCallMethod(t *testing.T) {
	m := new(MockLibrary)
	checks := []struct {
		method string
		args   string
		err    error
		result interface{}
	}{
		{method: "ThisDoesNotExist", err: ErrMethodNotFound},

		{method: "NoParam"},
		{method: "NoParam", args: "123", err: ErrInvalidArguments},
		{method: "NoParamResult", result: "toot gaya"},
		{method: "NoParamResult", args: "123", err: ErrInvalidArguments},
		{method: "NoParamNilError"},
		{method: "NoParamNilError", args: "123", err: ErrInvalidArguments},
		{method: "NoParamError", err: MockError},
		{method: "NoParamError", args: "123", err: ErrInvalidArguments},
		{method: "NoParamBadErrorReturn", err: ErrInvocationError},
		{method: "NoParamTwoResults", err: ErrInvocationError},

		{method: "OneParam", err: ErrInvalidArguments},
		{method: "OneParam", args: "123"},
		{method: "OneParam", args: "false", err: ErrInvalidArguments},
		{method: "OneParamEcho", err: ErrInvalidArguments},
		{method: "OneParamEcho", args: "123", err: ErrInvalidArguments},
		{method: "OneParamEcho", args: "\"foo\"", result: "foo"},
		{method: "OneParamEcho", args: "\"bar\"", result: "bar"},

		{method: "TwoParams", err: ErrInvocationError},
		{method: "TwoParams", args: "123", err: ErrInvocationError},

		{method: "ComplexParam", err: ErrInvalidArguments},
		{method: "ComplexParam", args: "123", err: ErrInvalidArguments},
		{method: "ComplexParam", args: "{\"foo\":42}", result: (*MockParams)(nil)},
		{method: "ComplexParam", args: "{\"foo\":42,\"breakit\":true}", err: ErrInvalidArguments},
		{method: "ComplexParam", args: "{\"foo\":42,\"nested\":{\"bar\":\"baz\"}}", result: &MockParams{Bar: "baz"}},
		{method: "ComplexParam", args: "null", err: ErrInvalidArguments},

		{method: "ArrayParam", args: "[4,8,15,16,23,42]", result: 6},
		{method: "ArrayParam", args: "[]", result: 0},
		{method: "ArrayParam", args: "null", err: ErrInvalidArguments},
		{method: "ArrayParam", args: "1,2,3,4", err: ErrInvalidArguments},
		{method: "ArrayParam", args: "", err: ErrInvalidArguments},
		{method: "ArrayParam", args: "{\"args\":[1,2,3]}", err: ErrInvalidArguments},

		{method: "ConditionalErrorNoResult", err: ErrInvalidArguments},
		{method: "ConditionalErrorNoResult", args: "0"},
		{method: "ConditionalErrorNoResult", args: "1", err: MockError},

		{method: "ConditionalErrorWithResult", err: ErrInvalidArguments},
		{method: "ConditionalErrorWithResult", args: " null  ", err: ErrInvalidArguments},
		{method: "ConditionalErrorWithResult", args: "\"null\"", err: ErrInvalidArguments},
		{method: "ConditionalErrorWithResult", args: "  0", result: "success"},
		{method: "ConditionalErrorWithResult", args: "6  ", result: "success"},
		{method: "ConditionalErrorWithResult", args: " 7 ", err: MockError},
		{method: "ConditionalErrorWithResult", args: "8", result: "success"},
	}
	for _, check := range checks {
		t.Run(check.method, func(t *testing.T) {
			err, result := callMethod(m, check.method, check.args)
			if !errors.Is(err, check.err) {
				t.Errorf("unexpected error: want %v got %v", check.err, err)
			} else {
				//t.Logf("got correct error: %v", err)
			}
			if !reflect.DeepEqual(result, check.result) {
				t.Errorf("unexpected result: want %v got %v", check.result, result)
			}
		})
	}
}
