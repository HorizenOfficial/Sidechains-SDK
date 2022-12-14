package test

import (
	"github.com/ethereum/go-ethereum/common"
	"math/rand"
)

func RandomBytes(n int) []byte {
	rng := make([]byte, n)
	rand.Read(rng)
	return rng
}

func RandomHash() common.Hash {
	return common.BytesToHash(RandomBytes(common.HashLength))
}

func RandomAddress() common.Address {
	return common.BytesToAddress(RandomBytes(common.AddressLength))
}
