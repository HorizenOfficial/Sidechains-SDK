package lib

import (
	"fmt"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/crypto"
	"github.com/ethereum/go-ethereum/log"
	"math/big"
)

var emptyCodeHash = crypto.Keccak256(nil)

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

type CodeHashParams struct {
	AccountParams
	CodeHash common.Hash `json:"codeHash"`
}

type CodeParams struct {
	AccountParams
	Code []byte `json:"code"`
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

type SnapshotParams struct {
	HandleParams
	RevisionId int `json:"revisionId"`
}

type GetLogsParams struct {
	AccountParams
	TxHash common.Hash `json:"txHash"`
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

// TODO: we might want to change this to "StateEmpty()" because the logic behind Exists() is a bit unpredictable
// "empty" means nonce==0 && balance==0 && codeHash==emptyHash
// on commit, empty accounts are automatically removed, therefore the following slightly confusion scenario is possible:
// - check existance of an account: false
// - touch the account in any way without actually changing it, e.g. setBalance(0)
// - check existance again: true
// - commit changes
// - check existance again: false (the account was removed on commit as it was still considered "empty")
func (s *Service) StateExists(params AccountParams) (error, bool) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, false
	}
	return nil, statedb.Exist(params.Address)
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

// StateSetCodeHash sets just the code hash, the code itself is set to nil
func (s *Service) StateSetCodeHash(params CodeHashParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	obj := statedb.GetOrNewStateObject(params.Address)
	obj.SetCode(params.CodeHash, nil)
	return nil
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

func (s *Service) StateGetStorage(params StorageParams) (error, common.Hash) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, common.Hash{}
	}
	value := statedb.GetState(params.Address, params.Key)
	return nil, value
}

func (s *Service) StateSetStorage(params SetStorageParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	statedb.SetState(params.Address, params.Key, params.Value)
	return nil
}

func (s *Service) StateRemoveStorage(params StorageParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	// the "empty" value will cause the key-value pair to be deleted
	statedb.SetState(params.Address, params.Key, common.Hash{})
	return nil
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
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, nil
	}
	length := int(statedb.GetState(params.Address, params.Key).Big().Int64())
	data := make([]byte, length)
	for start := 0; start < length; start += common.HashLength {
		chunkIndex := start / common.HashLength
		end := start + common.HashLength
		if end > length {
			end = length
		}
		chunk := statedb.GetState(params.Address, getChunkKey(params.Key, chunkIndex))
		copy(data[start:end], chunk.Bytes())
	}
	return nil, data
}

// StateSetStorageBytes writes values of arbitrary length to the storage trie of given account
func (s *Service) StateSetStorageBytes(params SetStorageBytesParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	if statedb.Empty(params.Address) {
		// if the account is empty any changes would be dropped during the commit phase
		return fmt.Errorf("account is empty, cannot modify storage trie: %v", params.Address)
	}
	// get previous length of value stored, if any
	oldLength := int(statedb.GetState(params.Address, params.Key).Big().Int64())
	// values are split up into 32-bytes chunks:
	// the length of the value is stored at the original key and the chunks are stored at hash(key, i)
	newLength := len(params.Value)
	// if the new value is empty remove all key-value pairs, including the one holding the value length
	statedb.SetState(params.Address, params.Key, common.BigToHash(big.NewInt(int64(newLength))))
	for start := 0; start < newLength || start < oldLength; start += common.HashLength {
		chunkIndex := start / common.HashLength
		var chunk common.Hash
		if start < newLength {
			end := start + common.HashLength
			if end > newLength {
				end = newLength
			}
			// (over-)write chunks
			chunk = packBytesIntoHash(params.Value[start:end])
		} else {
			// remove previous chunks that are not needed anymore
			chunk = common.Hash{}
		}
		statedb.SetState(params.Address, getChunkKey(params.Key, chunkIndex), chunk)
	}
	return nil
}

func (s *Service) StateRemoveStorageBytes(params StorageParams) error {
	deleteParams := SetStorageBytesParams{
		StorageParams: params,
		// the "empty" value will cause the key-value pair to be deleted
		Value: nil,
	}
	return s.StateSetStorageBytes(deleteParams)
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

func (s *Service) StateGetLogs(params GetLogsParams) (error, []*Log) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, nil
	}
	return nil, getLogs(statedb, params.TxHash)
}
