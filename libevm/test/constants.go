package test

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/crypto"
)

var (
	ZeroHash  = common.Hash{}
	EmptyHash = common.HexToHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
	NullHash  = common.BytesToHash(crypto.Keccak256(nil))
)
