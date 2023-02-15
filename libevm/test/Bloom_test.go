package test

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/types"
	"testing"
)

// replicate test in: com.horizen.account.receipt.BloomTest.bloomFilterTest
func TestGethBloom(t *testing.T) {
	data := common.FromHex("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")
	var bin types.Bloom
	bin.Add(data)
	t.Logf("bloom filter containing %s: %s", common.Bytes2Hex(data), common.Bytes2Hex(bin.Bytes()))
}
