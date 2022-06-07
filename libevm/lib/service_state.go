package lib

import (
	"bytes"
	"fmt"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/crypto"
	"github.com/ethereum/go-ethereum/log"
	"math"
)

var emptyCodeHash = crypto.Keccak256(nil)

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
	Amount *hexutil.Big `json:"amount"`
}

type NonceParams struct {
	AccountParams
	Nonce uint64 `json:"nonce"`
}

type StorageParams struct {
	AccountParams
	Key common.Hash `json:"key"`
}

type SetStorageParams struct {
	StorageParams
	Value common.Hash `json:"value"`
}

// StateOpen will create a new state at the given root hash.
// If the root hash is zero (or the hash of zero) this will give an empty trie.
// If the hash is anything else this will result in an error if the nodes cannot be found.
func (s *Service) StateOpen(params StateRootParams) (error, int) {
	// TODO: research if we want to use the snapshot feature
	statedb, err := state.New(params.Root, s.database, nil)
	if err != nil {
		log.Error("failed to open state", "root", params.Root, "error", err)
		return err, 0
	}
	// wrap around
	if s.stateHandle == math.MaxInt32 {
		s.stateHandle = 0
	}
	// this will never give a handle of 0, which is on purpose - we might consider a handle of 0 as invalid
	s.stateHandle++
	newHandle := s.stateHandle
	s.statedbs[newHandle] = statedb
	return nil, newHandle
}

func (s *Service) StateClose(params HandleParams) {
	delete(s.statedbs, params.Handle)
}

func (s *Service) getState(handle int) (error, *state.StateDB) {
	statedb := s.statedbs[handle]
	if statedb == nil {
		return fmt.Errorf("invalid state handle: %d", handle), nil
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

func (s *Service) StateGetBalance(params AccountParams) (error, *hexutil.Big) {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err, nil
	}
	stateObject := statedb.GetOrNewStateObject(params.Address)
	balance := stateObject.Balance()
	return nil, (*hexutil.Big)(balance)
}

func (s *Service) StateAddBalance(params BalanceParams) error {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err
	}
	statedb.AddBalance(params.Address, params.Amount.ToInt())
	return nil
}

func (s *Service) StateSubBalance(params BalanceParams) error {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err
	}
	statedb.SubBalance(params.Address, params.Amount.ToInt())
	return nil
}

func (s *Service) StateSetBalance(params BalanceParams) error {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err
	}
	statedb.SetBalance(params.Address, params.Amount.ToInt())
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

func (s *Service) StateGetCodeHash(params AccountParams) (error, common.Hash) {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err, common.Hash{}
	}
	return nil, statedb.GetCodeHash(params.Address)
}

func (s *Service) StateGetStorage(params StorageParams) (error, common.Hash) {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err, common.Hash{}
	}
	return nil, statedb.GetState(params.Address, params.Key)
}

// this is basically a copy of stateObject.empty() which is not exported and therefore not accessible here
func accountEmpty(statedb *state.StateDB, addr common.Address) bool {
	obj := statedb.GetOrNewStateObject(addr)
	return obj.Nonce() == 0 && obj.Balance().Sign() == 0 && bytes.Equal(obj.CodeHash(), emptyCodeHash)
}

func (s *Service) StateSetStorage(params SetStorageParams) error {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err
	}
	statedb.SetState(params.Address, params.Key, params.Value)
	// make sure that this account is not declared "empty" and then removed
	if accountEmpty(statedb, params.Address) {
		statedb.SetNonce(params.Address, 1)
	}
	return nil
}
