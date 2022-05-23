package overrides

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/core/state/snapshot"
	"github.com/ethereum/go-ethereum/log"
	"libevm/helper"
	"math/big"
)

type BalanceLog map[common.Address]*helper.BigInt

// ZenStateDB exists for one purpose: hook into calls from the EVM interpreter to AddBalance and SubBalance
// to keep track of and aggregate any balance changes during EVM execution.
type ZenStateDB struct {
	*state.StateDB
	BalanceChanges BalanceLog
}

// New creates a new state from a given trie.
func New(root common.Hash, db state.Database, snaps *snapshot.Tree) (*ZenStateDB, error) {
	statedb, err := state.New(root, db, snaps)
	if err != nil {
		return nil, err
	}
	zenstatedb := &ZenStateDB{
		StateDB:        statedb,
		BalanceChanges: make(BalanceLog),
	}
	return zenstatedb, err
}

func (s *ZenStateDB) addBalanceChange(addr common.Address, change *big.Int) {
	// bail out if the change is zero
	if change.Sign() == 0 {
		return
	}
	// aggregate changes for the same address
	if prev, exists := s.BalanceChanges[addr]; exists {
		prev.Add(prev.Int, change)
		// if balance changes result in a net-zero change now remove the entry
		if prev.Sign() == 0 {
			delete(s.BalanceChanges, addr)
		}
	} else {
		s.BalanceChanges[addr] = helper.NewBigInt(change)
	}
}

func (s *ZenStateDB) AddBalance(addr common.Address, amount *big.Int) {
	log.Debug("AddBalance", "addr", addr, "amount", amount)
	s.StateDB.AddBalance(addr, amount)
	s.addBalanceChange(addr, amount)
}

func (s *ZenStateDB) SubBalance(addr common.Address, amount *big.Int) {
	log.Debug("SubBalance", "addr", addr, "amount", amount)
	s.StateDB.SubBalance(addr, amount)
	s.addBalanceChange(addr, new(big.Int).Neg(amount))
}
