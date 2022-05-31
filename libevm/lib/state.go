package lib

import (
	"errors"
	"fmt"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/log"
	"libevm/types"
)

type StateRootParams struct {
	Root common.Hash `json:"root"`
}

type HandleParams struct {
	Handle int `json:"handle"`
}

type AccountParams struct {
	HandleParams
	Address common.Address `json:"address"`
}

type BalanceParams struct {
	AccountParams
	Amount *types.BigInt `json:"amount"`
}

type NonceParams struct {
	AccountParams
	Nonce uint64 `json:"nonce"`
}

// StateOpen will create a new state at the given root hash.
// If the root hash is zero (or the hash of zero) this will give an empty trie.
// If the hash is anything else this will result in an error if the nodes cannot be found.
func (s *Service) StateOpen(params StateRootParams) (error, int) {
	if s.database == nil {
		return errors.New("database not initialized"), 0
	}
	// TODO: research if we want to use the snapshot feature
	statedb, err := state.New(params.Root, s.database, nil)
	if err != nil {
		log.Error("failed to open state", "root", params.Root, "error", err)
		return err, 0
	}
	log.Debug("state opened", "root", params.Root)
	s.counter++
	handle := s.counter
	s.statedbs[handle] = statedb
	return nil, handle
}

func (s *Service) StateClose(params HandleParams) {
	delete(s.statedbs, params.Handle)
}

func (s *Service) getState(handle int) (error, *state.StateDB) {
	statedb := s.statedbs[handle]
	if statedb == nil {
		return errors.New(fmt.Sprintf("invalid state handle: %d", handle)), nil
	}
	return nil, s.statedbs[handle]
}

func (s *Service) StateIntermediateRoot(params HandleParams) (error, common.Hash) {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err, common.Hash{}
	}
	return nil, statedb.IntermediateRoot(true)
}

func (s *Service) StateCommit(params HandleParams) (error, common.Hash) {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err, common.Hash{}
	}
	hash, err := statedb.Commit(true)
	if err != nil {
		return err, common.Hash{}
	}
	err = statedb.Database().TrieDB().Commit(hash, false, nil)
	if err != nil {
		return err, common.Hash{}
	}
	return nil, hash
}

func (s *Service) StateGetBalance(params AccountParams) (error, *types.BigInt) {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err, nil
	}
	stateObject := statedb.GetOrNewStateObject(params.Address)
	balance := stateObject.Balance()
	log.Debug("account balance", "address", params.Address, "balance", balance)
	return nil, types.NewBigInt(balance)
}

func (s *Service) StateAddBalance(params BalanceParams) error {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err
	}
	statedb.AddBalance(params.Address, params.Amount.Int)
	return nil
}

func (s *Service) StateSubBalance(params BalanceParams) error {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err
	}
	statedb.SubBalance(params.Address, params.Amount.Int)
	return nil
}

func (s *Service) StateSetBalance(params BalanceParams) error {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err
	}
	statedb.SetBalance(params.Address, params.Amount.Int)
	return nil
}

func (s *Service) StateGetNonce(params AccountParams) (error, uint64) {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err, 0
	}
	return nil, statedb.GetNonce(params.Address)
}

func (s *Service) StateSetNonce(params NonceParams) error {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err
	}
	statedb.SetNonce(params.Address, params.Nonce)
	return nil
}
