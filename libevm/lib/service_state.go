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
	Nonce uint64 `json:"nonce"`
}

type StorageParams struct {
	AccountParams
	Key common.Hash `json:"key"`
}

type SetStorageParams struct {
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

// make sure that the given account is not declared "empty" and then removed
func preserveAccount(statedb *state.StateDB, addr common.Address) {
	obj := statedb.GetOrNewStateObject(addr)
	// this is basically a copy of stateObject.empty() which is not exported and therefore not accessible here
	empty := obj.Nonce() == 0 && obj.Balance().Sign() == 0 && bytes.Equal(obj.CodeHash(), emptyCodeHash)
	if empty {
		obj.SetCode(crypto.Keccak256Hash(addr.Bytes()), make([]byte, 0))
	}
}

var chunkedMagicValue = common.HexToHash("0x55253b59b75c20e73965d68d3673a76f42f6415752c6fa3ce7a11c3ee5689aec")

// chunk keys are generating by hashing the original key and the chunk index
func getChunkKey(key common.Hash, chunkIndex int) common.Hash {
	chunkIndexBytes := big.NewInt(int64(chunkIndex)).Bytes()
	hashedData := append(key.Bytes(), chunkIndexBytes...)
	return crypto.Keccak256Hash(hashedData)
}

func (s *Service) StateGetStorage(params StorageParams) (error, []byte) {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err, nil
	}
	value := statedb.GetState(params.Address, params.Key)
	if value != chunkedMagicValue {
		return nil, value.Bytes()
	}
	length := int(statedb.GetState(params.Address, getChunkKey(params.Key, 0)).Big().Int64())
	data := make([]byte, length)
	for i := 0; i < length; i += common.HashLength {
		end := i + common.HashLength
		if end > length {
			end = length
		}
		chunk := statedb.GetState(params.Address, getChunkKey(params.Key, i+1)).Bytes()
		copy(data[i:end], chunk)
	}
	return nil, data
}

// StateSetStorage writes values of arbitrary length to the storage trie of given account
func (s *Service) StateSetStorage(params SetStorageParams) error {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err
	}
	if len(params.Value) <= common.HashLength {
		// Values shorter than 32 bytes can be stored directly as-is.
		statedb.SetState(params.Address, params.Key, common.BytesToHash(params.Value))
	} else {
		// Values longer than 32 bytes are split up to as many chunks of 32-bytes length as necessary.
		// To mark this value as chunked a magic number is stored at the original key, the length of the value is
		// stored at hash(key, 0) and the chunks are stored at hash(key, i), where is the 1-based index of the chunk.

		// store a magic number at the original key
		statedb.SetState(params.Address, params.Key, chunkedMagicValue)
		// and store the number of chunks at hash(key, 0)
		statedb.SetState(params.Address, getChunkKey(params.Key, 0), common.BigToHash(big.NewInt(int64(len(params.Value)))))
		// store the chunks
		for i := 0; i < len(params.Value); i += common.HashLength {
			end := i + common.HashLength
			if end > len(params.Value) {
				end = len(params.Value)
			}
			statedb.SetState(params.Address, getChunkKey(params.Key, i+1), common.BytesToHash(params.Value[i:end]))
		}
	}
	preserveAccount(statedb, params.Address)
	return nil
}
