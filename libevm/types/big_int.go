package types

import (
	"fmt"
	"math/big"
)

const JsonBase = 10

// BigInt wraps the big.Int type and overrides the marshalling behavior for JSON, which is the only reason for its
// existence. Per default big.Int serializes to a JSON number type which is problematic as any number outside the
// JavaScript number spec (IEEE 64-bit double) is prone to loss of precision when JSON is parsed according to spec.
type BigInt struct {
	internal *big.Int
}

func NewInt(x int64) *BigInt {
	return &BigInt{big.NewInt(x)}
}

func NewBigInt(x *big.Int) *BigInt {
	z := new(big.Int)
	z.Set(x)
	return &BigInt{z}
}

func (b *BigInt) Unwrap() *big.Int {
	if b != nil {
		return b.internal
	}
	return nil
}

func (b BigInt) MarshalJSON() ([]byte, error) {
	// wrap in quotes because the JSON number type is just a double
	// without quotes this would be out of spec and risk a loss of precision
	return []byte("\"" + b.internal.Text(JsonBase) + "\""), nil
}

func (b *BigInt) UnmarshalJSON(p []byte) error {
	if string(p) == "null" {
		return nil
	}
	var z big.Int
	_, ok := z.SetString(string(p[1:len(p)-1]), JsonBase)
	if !ok {
		return fmt.Errorf("not a valid big integer: %s", p)
	}
	b.internal = &z
	return nil
}
