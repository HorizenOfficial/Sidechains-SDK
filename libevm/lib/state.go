package lib

import (
	"errors"
	"fmt"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/log"
)

type StateRootParams struct {
	Root common.Hash
}

type HandleParams struct {
	Handle int
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
		return errors.New(fmt.Sprintf("invalid state Handle: %d", handle)), nil
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
	return nil, hash
}
