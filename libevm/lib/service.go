package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/core/vm"
	"github.com/ethereum/go-ethereum/crypto"
	"math/big"
)

type BlockHashCallback func(int, uint64) common.Hash

type Service struct {
	databases       *Handles[*Database]
	statedbs        *Handles[*state.StateDB]
	blockHashGetter BlockHashCallback
}

func New() *Service {
	return &Service{
		databases: NewHandles[*Database](),
		statedbs:  NewHandles[*state.StateDB](),
	}
}

func NewWithCallback(callback BlockHashCallback) *Service {
	return &Service{
		databases:       NewHandles[*Database](),
		statedbs:        NewHandles[*state.StateDB](),
		blockHashGetter: callback,
	}
}

func (s *Service) createBlockHashGetter(handle int) vm.GetHashFunc {
	// default to the mocked block hash getter whenever there is no callback set to retrieve actual block hashes,
	// e.g. during tests
	if s.blockHashGetter == nil {
		return mockBlockHashFn
	}
	// return a function to retrieve the block hash for a given block number, passing on the handle
	return func(blockNumber uint64) common.Hash {
		return s.blockHashGetter(handle, blockNumber)
	}
}

func mockBlockHashFn(blockNumber uint64) common.Hash {
	return common.BytesToHash(crypto.Keccak256([]byte(new(big.Int).SetUint64(blockNumber).String())))
}
