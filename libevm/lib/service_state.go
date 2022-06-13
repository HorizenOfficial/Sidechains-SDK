package lib

import (
	"bytes"
	"fmt"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/crypto"
	"github.com/ethereum/go-ethereum/log"
	"math/big"
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
	Nonce hexutil.Uint64 `json:"nonce"`
}

type CodeParams struct {
	AccountParams
	Code     []byte      `json:"code"`
	CodeHash common.Hash `json:"codeHash"`
}

type StorageParams struct {
	AccountParams
	Key common.Hash `json:"key"`
}

type SetStorageParams struct {
	StorageParams
	Value common.Hash `json:"value"`
}

type SetStorageBytesParams struct {
	StorageParams
	Value []byte `json:"value"`
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
	return nil, s.statedbs.Add(statedb)
}

func (s *Service) StateClose(params HandleParams) {
	s.statedbs.Remove(params.Handle)
}

func (s *Service) getState(handle int) (error, *state.StateDB) {
	err, statedb := s.statedbs.Get(handle)
	if err != nil {
		return err, nil
	}
	return nil, statedb.(*state.StateDB)
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

func (s *Service) StateGetNonce(params AccountParams) (error, hexutil.Uint64) {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err, 0
	}
	return nil, (hexutil.Uint64)(statedb.GetNonce(params.Address))
}

func (s *Service) StateSetNonce(params NonceParams) error {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err
	}
	statedb.SetNonce(params.Address, uint64(params.Nonce))
	return nil
}

func (s *Service) StateGetCodeHash(params AccountParams) (error, common.Hash) {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err, common.Hash{}
	}
	return nil, statedb.GetCodeHash(params.Address)
}

func (s *Service) StateSetCode(params CodeParams) error {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err
	}
	obj := statedb.GetOrNewStateObject(params.Address)
	code := params.Code
	if len(code) == 0 {
		code = make([]byte, 0)
	}
	obj.SetCode(params.CodeHash, code)
	return nil
}

func (s *Service) StateGetStorage(params StorageParams) (error, common.Hash) {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err, common.Hash{}
	}
	value := statedb.GetState(params.Address, params.Key)
	return nil, value
}

func (s *Service) StateSetStorage(params SetStorageParams) error {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err
	}
	statedb.SetState(params.Address, params.Key, params.Value)
	return nil
}

// this is basically a copy of stateObject.empty() which is not exported and therefore not accessible here
func isAccountEmpty(statedb *state.StateDB, addr common.Address) bool {
	obj := statedb.GetOrNewStateObject(addr)
	return obj.Nonce() == 0 && obj.Balance().Sign() == 0 && bytes.Equal(obj.CodeHash(), emptyCodeHash)
}

// chunk keys are generated by hashing the original key and the chunk index
func getChunkKey(key common.Hash, chunkIndex int) common.Hash {
	chunkIndexBytes := big.NewInt(int64(chunkIndex)).Bytes()
	hashedData := append(key.Bytes(), chunkIndexBytes...)
	return crypto.Keccak256Hash(hashedData)
}

// make sure we add trailing zeros, not leading zeroes
func packBytesIntoHash(bytes []byte) common.Hash {
	if len(bytes) < common.HashLength {
		tmp := make([]byte, common.HashLength)
		copy(tmp, bytes)
		return common.BytesToHash(tmp)
	} else {
		return common.BytesToHash(bytes)
	}
}

func (s *Service) StateGetStorageBytes(params StorageParams) (error, []byte) {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err, nil
	}
	length := int(statedb.GetState(params.Address, params.Key).Big().Int64())
	data := make([]byte, length)
	for i := 0; i < length; i += common.HashLength {
		end := i + common.HashLength
		if end > length {
			end = length
		}
		chunk := statedb.GetState(params.Address, getChunkKey(params.Key, i))
		copy(data[i:end], chunk.Bytes())
	}
	return nil, data
}

// StateSetStorageBytes writes values of arbitrary length to the storage trie of given account
func (s *Service) StateSetStorageBytes(params SetStorageBytesParams) error {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err
	}
	if isAccountEmpty(statedb, params.Address) {
		// if the account is empty any changes would be dropped during the commit phase
		return fmt.Errorf("account is empty, cannot modify storage trie: %v", params.Address)
	}
	// Values are split up to as many chunks of 32-bytes length as necessary.
	// The length of the value is stored at the original key and the chunks are stored at hash(key, i).
	length := len(params.Value)
	statedb.SetState(params.Address, params.Key, common.BigToHash(big.NewInt(int64(length))))
	for i := 0; i < length; i += common.HashLength {
		end := i + common.HashLength
		if end > length {
			end = length
		}
		statedb.SetState(params.Address, getChunkKey(params.Key, i), packBytesIntoHash(params.Value[i:end]))
	}
	return nil
}
