package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/log"
)

type StateParams struct {
	DatabaseParams
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
	Nonce hexutil.Uint64 `json:"nonce"`
}

type CodeParams struct {
	AccountParams
	Code []byte `json:"code"`
}

type SnapshotParams struct {
	HandleParams
	RevisionId int `json:"revisionId"`
}

// StateOpen will create a new state at the given root hash.
// If the root hash is zero (or the hash of zero) this will give an empty trie.
// If the hash is anything else this will result in an error if the nodes cannot be found.
func (s *Service) StateOpen(params StateParams) (error, int) {
	err, db := s.databases.Get(params.DatabaseHandle)
	if err != nil {
		return err, 0
	}
	// TODO: research if we want to use the snapshot feature
	statedb, err := state.New(params.Root, db.database, nil)
	if err != nil {
		log.Error("failed to open state", "root", params.Root, "error", err)
		return err, 0
	}
	return nil, s.statedbs.Add(statedb)
}

func (s *Service) StateClose(params HandleParams) {
	s.statedbs.Remove(params.Handle)
}

func (s *Service) StateFinalize(params HandleParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	statedb.Finalise(true)
	return nil
}

func (s *Service) StateIntermediateRoot(params HandleParams) (error, common.Hash) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, common.Hash{}
	}
	return nil, statedb.IntermediateRoot(true)
}

func (s *Service) StateCommit(params HandleParams) (error, common.Hash) {
	err, statedb := s.statedbs.Get(params.Handle)
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

// StateEmpty tests if the given account is empty,
// "empty" means non-existent or nonce==0 && balance==0 && codeHash==emptyHash (hash of nil)
func (s *Service) StateEmpty(params AccountParams) (error, bool) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, false
	}
	return nil, statedb.Empty(params.Address)
}

func (s *Service) StateGetBalance(params AccountParams) (error, *hexutil.Big) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, nil
	}
	return nil, (*hexutil.Big)(statedb.GetBalance(params.Address))
}

func (s *Service) StateAddBalance(params BalanceParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	statedb.AddBalance(params.Address, params.Amount.ToInt())
	return nil
}

func (s *Service) StateSubBalance(params BalanceParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	statedb.SubBalance(params.Address, params.Amount.ToInt())
	return nil
}

func (s *Service) StateSetBalance(params BalanceParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	statedb.SetBalance(params.Address, params.Amount.ToInt())
	return nil
}

func (s *Service) StateGetNonce(params AccountParams) (error, hexutil.Uint64) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, 0
	}
	return nil, (hexutil.Uint64)(statedb.GetNonce(params.Address))
}

func (s *Service) StateSetNonce(params NonceParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	statedb.SetNonce(params.Address, uint64(params.Nonce))
	return nil
}

func (s *Service) StateGetCodeHash(params AccountParams) (error, common.Hash) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, common.Hash{}
	}
	return nil, statedb.GetCodeHash(params.Address)
}

func (s *Service) StateGetCode(params AccountParams) (error, []byte) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, nil
	}
	return nil, statedb.GetCode(params.Address)
}

// StateSetCode sets the given code, the code hash is updated automatically
func (s *Service) StateSetCode(params CodeParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	statedb.SetCode(params.Address, params.Code)
	return nil
}

func (s *Service) StateSnapshot(params HandleParams) (error, int) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, 0
	}
	return nil, statedb.Snapshot()
}

func (s *Service) StateRevertToSnapshot(params SnapshotParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	statedb.RevertToSnapshot(params.RevisionId)
	return nil
}
