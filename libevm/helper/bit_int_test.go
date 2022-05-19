package helper

import (
	"bytes"
	"encoding/json"
	"github.com/ethereum/go-ethereum/common"
	"math/big"
	"reflect"
	"testing"
)

type testObject struct {
	Text           string                     `json:"text"`
	Big            *BigInt                    `json:"big"`
	Sender         common.Address             `json:"sender"`
	BalanceChanges map[common.Address]*BigInt `json:"balanceChanges"`
}

var (
	largeNumber, _ = new(big.Int).SetString("100000000000000000000000000000000000000001238761238", 10)
	input          = testObject{
		Text:   "narf",
		Big:    NewBigInt(largeNumber),
		Sender: common.HexToAddress("0x0123456789000000000000000000000000000000"),
		BalanceChanges: map[common.Address]*BigInt{
			common.HexToAddress("0x0123456789ABCDEF000000000000000000000000"): NewInt(1234),
			common.HexToAddress("0x000000000000000000000000FFFFFFFFFFFFFFFF"): NewInt(5678),
		},
	}
)

const expected = `{"text":"narf","big":"100000000000000000000000000000000000000001238761238","sender":"0x0123456789000000000000000000000000000000","balanceChanges":{"0x000000000000000000000000ffffffffffffffff":"5678","0x0123456789abcdef000000000000000000000000":"1234"}}`

func TestBigInt_MarshalJSON(t *testing.T) {
	out, err := json.Marshal(input)
	if err != nil {
		t.Fatal("failed to serialize object", err)
		return
	}
	t.Log("serialized object", string(out))
	if !bytes.Equal(out, []byte(expected)) {
		t.Error("expected output", expected)
	}
}

func TestBigInt_UnmarshalJSON(t *testing.T) {
	var out testObject
	err := json.Unmarshal([]byte(expected), &out)
	if err != nil {
		t.Fatal("failed to parse json", err)
		return
	}
	t.Log("deserialized object", out)
	if !reflect.DeepEqual(input, out) {
		t.Error("actual output", out)
	}
}
