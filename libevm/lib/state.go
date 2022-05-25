package lib

// #cgo CFLAGS: -g -Wall -O3 -fpic -Werror
import "C"
import (
	"errors"
	"fmt"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/log"
)

// OpenState will create a new state at the given root hash.
// If the root hash is zero (or the hash of zero) this will give an empty trie.
// If the hash is anything else this will result in an error if the nodes cannot be found.
func (s *Service) OpenState(params struct{ Root common.Hash }) (error, int) {
	if s.database == nil {
		return errors.New("database not initialized"), 0
	}
	// TODO: research if we want to use the snapshot feature
	statedb, err := state.New(params.Root, s.database, nil)
	if err != nil {
		log.Error("failed to open state", "rootHash", params.Root, "error", err)
		return err, 0
	}
	s.counter++
	handle := s.counter
	s.statedbs[handle] = statedb
	return nil, handle
}

func (s *Service) getState(handle int) (error, *state.StateDB) {
	statedb := s.statedbs[handle]
	if statedb == nil {
		return errors.New(fmt.Sprintf("invalid state handle: %d", handle)), nil
	}
	return nil, s.statedbs[handle]
}

func (s *Service) CloseState(params struct{ handle int }) {
	delete(s.statedbs, params.handle)
}
