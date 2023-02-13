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

// Concat efficiently merges all gives slices
func Concat(slices ...[]byte) []byte {
	// determine total size
	var length int
	for _, s := range slices {
		length += len(s)
	}
	// preallocate result slice
	tmp := make([]byte, length)
	// copy all inputs into result
	var i int
	for _, s := range slices {
		i += copy(tmp[i:], s)
	}
	return tmp
}
