package types

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/ethereum/go-ethereum/core/vm/runtime"
)

// SerializableConfig mirrors configurable parts of the runtime.Config
type SerializableConfig struct {
	Difficulty  *hexutil.Big   `json:"difficulty"`
	Origin      common.Address `json:"origin"`
	Coinbase    common.Address `json:"coinbase"`
	BlockNumber *hexutil.Big   `json:"blockNumber"`
	Time        *hexutil.Big   `json:"time"`
	GasLimit    uint64         `json:"gasLimit"`
	GasPrice    *hexutil.Big   `json:"gasPrice"`
	Value       *hexutil.Big   `json:"value"`
	BaseFee     *hexutil.Big   `json:"baseFee"`
}

// GetConfig maps SerializableConfig to runtime.Config
func (c *SerializableConfig) GetConfig() *runtime.Config {
	return &runtime.Config{
		ChainConfig: nil,
		Difficulty:  c.Difficulty.ToInt(),
		Origin:      c.Origin,
		Coinbase:    c.Coinbase,
		BlockNumber: c.BlockNumber.ToInt(),
		Time:        c.Time.ToInt(),
		GasLimit:    c.GasLimit,
		GasPrice:    c.GasPrice.ToInt(),
		Value:       c.Value.ToInt(),
		BaseFee:     c.BaseFee.ToInt(),
	}
}
