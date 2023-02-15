package lib

import (
	"errors"
	"fmt"
	"github.com/ethereum/go-ethereum/common"
	"libevm/lib/geth_internal"
)

// ErrEmptyAccount is thrown when attempting to write to a storage trie of an otherwise "empty" account
// to prevent potential data loss, because empty accounts will be pruned, regardless of data in the storage trie
var ErrEmptyAccount = errors.New("account is empty, cannot modify storage trie")

type StorageParams struct {
	AccountParams
	Key common.Hash `json:"key"`
}

type SetStorageParams struct {
	StorageParams
	Value common.Hash `json:"value"`
}

type ProofParams struct {
	AccountParams
	StorageKeys []string `json:"storageKeys"`
}

func (s *Service) StateGetStorage(params StorageParams) (error, common.Hash) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, common.Hash{}
	}
	value := statedb.GetState(params.Address, params.Key)
	return nil, value
}

func (s *Service) StateGetCommittedStorage(params StorageParams) (error, common.Hash) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, common.Hash{}
	}
	value := statedb.GetCommittedState(params.Address, params.Key)
	return nil, value
}

func (s *Service) StateSetStorage(params SetStorageParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	if statedb.Empty(params.Address) {
		// if the account is empty any changes would be dropped during the commit phase
		return fmt.Errorf("%w: %v", ErrEmptyAccount, params.Address)
	}
	statedb.SetState(params.Address, params.Key, params.Value)
	return nil
}

func (s *Service) StateGetProof(params ProofParams) (error, *geth_internal.AccountResult) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, nil
	}
	result, err := geth_internal.GetProof(statedb, params.Address, params.StorageKeys)
	return err, result
}
