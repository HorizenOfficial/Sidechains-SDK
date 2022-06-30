package lib

import (
	"bytes"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/core/types"
	"github.com/ethereum/go-ethereum/crypto"
	"github.com/ethereum/go-ethereum/signer/core/apitypes"
	"github.com/ethereum/go-ethereum/trie"
	"math/big"
	"testing"
)

var nullHash = common.Hash{}
var emptyHash = common.BytesToHash(crypto.Keccak256(nil))

func checkValue(t *testing.T, expected []byte, actual []byte) {
	if !bytes.Equal(expected, actual) {
		t.Errorf(
			"storage value mismatch:\n"+
				"expected: %v\n"+
				"actual:   %v",
			common.Bytes2Hex(expected),
			common.Bytes2Hex(actual),
		)
	}
}

func testStorageSetCommitWrite(t *testing.T, instance *Service, dbHandle int, addr common.Address, key common.Hash, value common.Hash) {
	var err error
	err, handleInt := instance.StateOpen(StateParams{
		DatabaseParams: DatabaseParams{DatabaseHandle: dbHandle},
		Root:           common.Hash{},
	})
	if err != nil {
		t.Error(err)
	}
	handle := HandleParams{
		Handle: handleInt,
	}
	account := AccountParams{
		HandleParams: handle,
		Address:      addr,
	}
	storage := StorageParams{
		AccountParams: account,
		Key:           key,
	}
	// make sure the account is not "empty"
	_ = instance.StateSetCodeHash(CodeHashParams{
		AccountParams: account,
		CodeHash:      crypto.Keccak256Hash(addr.Bytes()),
	})
	err, initialRoot := instance.StateIntermediateRoot(handle)
	err = instance.StateSetStorage(SetStorageParams{
		StorageParams: storage,
		Value:         value,
	})
	if err != nil {
		t.Error(err)
	}
	err, retrievedValue := instance.StateGetStorage(storage)
	if err != nil {
		t.Error(err)
	}
	checkValue(t, value.Bytes(), retrievedValue.Bytes())
	err, committedRoot := instance.StateCommit(handle)
	if err != nil {
		t.Error(err)
	}
	err, committedValue := instance.StateGetStorage(storage)
	if err != nil {
		t.Error(err)
	}
	checkValue(t, value.Bytes(), committedValue.Bytes())
	instance.StateClose(handle)

	// reopen state at the committed root
	err, handleInt = instance.StateOpen(StateParams{
		DatabaseParams: DatabaseParams{DatabaseHandle: dbHandle},
		Root:           committedRoot,
	})
	if err != nil {
		t.Error(err)
	}
	// update helpers with new handle
	handle.Handle = handleInt
	account.Handle = handleInt
	storage.Handle = handleInt

	err, writtenValue := instance.StateGetStorage(storage)
	if err != nil {
		t.Error(err)
	}
	checkValue(t, value.Bytes(), writtenValue.Bytes())

	err = instance.StateRemoveStorage(storage)
	if err != nil {
		t.Error(err)
	}
	err, removedRoot := instance.StateIntermediateRoot(handle)
	if removedRoot != initialRoot {
		t.Error("incomplete removal of state storage")
	}

	instance.StateClose(handle)
}

func testStorageBytesSetCommitWrite(t *testing.T, instance *Service, dbHandle int, addr common.Address, key common.Hash, value []byte) {
	var err error
	err, handleInt := instance.StateOpen(StateParams{
		DatabaseParams: DatabaseParams{DatabaseHandle: dbHandle},
		Root:           common.Hash{}})
	if err != nil {
		t.Error(err)
	}
	handle := HandleParams{
		Handle: handleInt,
	}
	account := AccountParams{
		HandleParams: handle,
		Address:      addr,
	}
	storage := StorageParams{
		AccountParams: account,
		Key:           key,
	}
	// make sure the account is not "empty"
	_ = instance.StateSetCodeHash(CodeHashParams{
		AccountParams: account,
		CodeHash:      crypto.Keccak256Hash(addr.Bytes()),
	})
	err, initialRoot := instance.StateIntermediateRoot(handle)
	err = instance.StateSetStorageBytes(SetStorageBytesParams{
		StorageParams: storage,
		Value:         value,
	})
	if err != nil {
		t.Error(err)
	}
	err, retrievedValue := instance.StateGetStorageBytes(storage)
	if err != nil {
		t.Error(err)
	}
	checkValue(t, value, retrievedValue)
	err, committedRoot := instance.StateCommit(handle)
	if err != nil {
		t.Error(err)
	}
	err, committedValue := instance.StateGetStorageBytes(storage)
	if err != nil {
		t.Error(err)
	}
	checkValue(t, value, committedValue)
	instance.StateClose(handle)

	// reopen state at the committed root
	err, handleInt = instance.StateOpen(StateParams{
		DatabaseParams: DatabaseParams{DatabaseHandle: dbHandle},
		Root:           committedRoot,
	})
	if err != nil {
		t.Error(err)
	}
	// update helpers with new handle
	handle.Handle = handleInt
	account.Handle = handleInt
	storage.Handle = handleInt

	err, writtenValue := instance.StateGetStorageBytes(storage)
	if err != nil {
		t.Error(err)
	}
	checkValue(t, value, writtenValue)

	err = instance.StateRemoveStorageBytes(storage)
	if err != nil {
		t.Error(err)
	}
	err, removedRoot := instance.StateIntermediateRoot(handle)
	if removedRoot != initialRoot {
		t.Error("incomplete removal of state storage")
	}

	instance.StateClose(handle)
}

func TestStateStorage(t *testing.T) {
	var (
		instance = New()
		err      error
		addr     = common.HexToAddress("0x0011223344556677889900112233445566778899")
		key      = common.BytesToHash(crypto.Keccak256(common.Hex2Bytes("44556677")))
		values   = []common.Hash{
			common.HexToHash("1234"),
			common.HexToHash("5ac11866e5b303e74e94652f3fdcf44d292f2f82"),
			common.HexToHash("5ac11866e36c8d1a9195cb74789a97e15b303e74e94652f3fdcf44d292f2f829"),
			common.HexToHash("572f59c7e67c4e1733b2a349db3fc3f50e7b6d2d2b08419957937678d647b40a47"),
		}
	)
	err, dbHandle := instance.OpenMemoryDB()
	if err != nil {
		t.Error(err)
	}
	for _, value := range values {
		testStorageSetCommitWrite(t, instance, dbHandle, addr, key, value)
	}
	_ = instance.CloseDatabase(DatabaseParams{DatabaseHandle: dbHandle})
}

func TestStateStorageBytes(t *testing.T) {
	var (
		instance = New()
		err      error
		addr     = common.HexToAddress("0x0011223344556677889900112233445566778899")
		key      = common.BytesToHash(crypto.Keccak256(common.Hex2Bytes("00112233")))
		values   = [][]byte{
			common.Hex2Bytes("1234"),
			common.Hex2Bytes("5ac11866e36c8d1a9195cb74789a97e15b303e74e94652f3fdcf44d292f2f829"),
			common.Hex2Bytes("572f59c7e67c4e1733b2a349db3fc3f50e7b6d2d2b08419957937678d647b40a4706f4668e14d832a9e239cc5f22b93988e2238b469bdd1654d4445111d2d7cb1481a443645c9d2563e15d6a577cc89295c69f5e0165"),
			common.Hex2Bytes("c359e9d0c2cffce02fc6e55e637cef893595294bfea05ad48a54d5659a8fdb552797c0214354aa4d0cde268d9fe046f321760a65beb6e8a17d6407f5f7d74c3bec075049f764f184599dc4fae3e0f1acf4aadd70fea64eb71131ee1f7f522a7b960c1a344a8c58e657e66033af7a3f67a80b9e459af9107ba19825a3991bc930e62a1a912cbe808b89c795ab3b6b098d25759925ad85c2eb25646a245cef8fe32417d549a462560b831e07786335103eaa4ea4404cc9ee844c6471ca721d864f45014dc7a41cf91845722e28346e115f4acc3acd9203881f4101964243ce46a66e2f7166465893b4f9097c842e67533b1292d78f42767be0a71f1b797b3ecfa19bef54d3b33f31571ba9e3ccf20399c1493361c517a3517be7916fb4413342a133c5c5df6e2649b5a4dc949f04356ac8be51192335813a1bb7bb612ab29f27a1decdc3232075f213b3357404437e529c790589b2934d409c89bf7132e3786b8b0592d8cd5c306d7ec3cc0b2e413ee1057e4893dbbdf74d92915a176ae7c9606a262d923f9baaeea7aefbf2be499348a15a2c6fe9a26cad513d3a3a9afba638fbf27181fa93fbe4864144c7d6aa6e27df2668ad50adbce634237728427b6db5a1c7b3574a4dda57b08026142db7a607b48c2149eb2670429c5cf3258c61f01a264893dbf951c40e707e5d3b13693a6c35c0760d32aad8a964937acabbd47e24ff3bc3d4473b6dd97bde69ad194f2eaa39b28530a00ea05de0824d12f5c45cbf737b1ef06839571340447d56b307ad1053f9211734d5a49e5d2b8a5642f9d769fb2bee816026d7c3f732060a0bf7e6db832ff033b59cd4cb36d0b04b2390d4c06f56c7a7ac5ecf0ea3dba316c8e153668ce4d1532c5443c6501fc02508c89b14029a"),
		}
	)
	err, dbHandle := instance.OpenMemoryDB()
	if err != nil {
		t.Error(err)
	}
	for _, value := range values {
		testStorageBytesSetCommitWrite(t, instance, dbHandle, addr, key, value)
	}
	_ = instance.CloseDatabase(DatabaseParams{DatabaseHandle: dbHandle})
}

func TestRawStateDB(t *testing.T) {
	var (
		instance = New()
		addr     = common.HexToAddress("0x0011223344556677889900112233445566778899")
		key      = common.BytesToHash(crypto.Keccak256(common.Hex2Bytes("00112233")))
		value    = common.HexToHash("0x1234")
	)
	_, dbHandle := instance.OpenLevelDB(LevelDBParams{Path: t.TempDir()})
	_, db := instance.databases.Get(dbHandle)
	statedb, _ := state.New(common.Hash{}, db.database, nil)
	initialCodeHash := statedb.GetCodeHash(addr)
	if initialCodeHash != nullHash {
		t.Errorf("code hash of non-existant account should be zero, got %v", initialCodeHash)
	}
	// set a non-empty value to the account to make sure it exists and is not "empty"
	statedb.SetNonce(addr, 1)
	emptyCodeHash := statedb.GetCodeHash(addr)
	if emptyCodeHash != emptyHash {
		t.Errorf("code hash of empty account should be hash of nil, got %v", emptyCodeHash)
	}
	revid := statedb.Snapshot()
	statedb.SetState(addr, key, value)
	retrievedValue := statedb.GetState(addr, key)
	if retrievedValue != value {
		t.Error("value not set correctly")
	}
	statedb.RevertToSnapshot(revid)
	revertedValue := statedb.GetState(addr, key)
	if revertedValue != nullHash {
		t.Error("snapshot rollback failed")
	}
	statedb.SetState(addr, key, value)
	hash, _ := statedb.Commit(true)
	_ = statedb.Database().TrieDB().Commit(hash, false, nil)
	committedValue := statedb.GetState(addr, key)
	if committedValue != value {
		t.Error("value not committed correctly")
	}
	_ = instance.CloseDatabase(DatabaseParams{DatabaseHandle: dbHandle})
}

func generateTransactions(count int) types.Transactions {
	txs := make(types.Transactions, 0)
	var (
		to       = common.NewMixedcaseAddress(common.HexToAddress("0x1337"))
		gas      = hexutil.Uint64(21000)
		gasPrice = (hexutil.Big)(*big.NewInt(2000000000))
		data     = hexutil.Bytes(common.Hex2Bytes("01020304050607080a"))
	)
	for v := 0; v < count; v++ {
		value := (hexutil.Big)(*new(big.Int).Mul(big.NewInt(1e18), big.NewInt(int64(v))))
		nonce := (hexutil.Uint64)(v)
		tx := apitypes.SendTxArgs{
			To:    &to,
			Gas:   gas,
			Value: value,
			Data:  &data,
			Nonce: nonce,
		}
		//if v%3 == 0 {
		// legacy TX
		tx.GasPrice = &gasPrice
		//} else {
		//	// dynamic fee TX
		//	tx.MaxFeePerGas = &gasPrice
		//	tx.MaxPriorityFeePerGas = &gasPrice
		//}
		txs = append(txs, tx.ToTransaction())
	}
	return txs
}

func generateReceipts(count int) types.Receipts {
	receipts := make(types.Receipts, 0)
	for v := 0; v < count; v++ {
		status := types.ReceiptStatusSuccessful
		// mark a number of receipts as failed
		if v%7 == 0 {
			status = types.ReceiptStatusFailed
		}
		receipt := &types.Receipt{
			// valid types are 0, 1 and 2
			Type:              uint8(v % 3),
			CumulativeGasUsed: uint64(v * 1000),
			Status:            status,
			TxHash:            crypto.Keccak256Hash(big.NewInt(int64(41271*count + v)).Bytes()),
		}
		// Set the receipt logs and create the bloom filter.
		receipt.Logs = make([]*types.Log, 0)
		receipt.Bloom = types.CreateBloom(types.Receipts{receipt})
		// These three are non-consensus fields:
		//receipt.BlockHash
		//receipt.BlockNumber
		receipt.TransactionIndex = uint(v)
		receipts = append(receipts, receipt)
	}
	return receipts
}

func verifyRootHash(t *testing.T, instance *Service, list types.DerivableList) common.Hash {
	var (
		expectedRoot = types.DeriveSha(list, trie.NewStackTrie(nil))
		valueBuf     = new(bytes.Buffer)
		values       = make([][]byte, 0)
	)
	length := list.Len()
	// RLP encode receipts
	for i := 0; i < length; i++ {
		valueBuf.Reset()
		list.EncodeIndex(i, valueBuf)
		values = append(values, common.CopyBytes(valueBuf.Bytes()))
	}
	err, actualRoot := instance.HashRoot(HashParams{
		Values: values,
	})
	if err != nil {
		t.Errorf("error hashing: %v", err)
	} else if actualRoot != expectedRoot {
		t.Errorf("got wrong root hash: expected %v got %v", expectedRoot, actualRoot)
	}
	// explicitly make sure we get the empty root hash for an empty trie
	if length == 0 && actualRoot != types.EmptyRootHash {
		t.Errorf("got wrong root hash for empty trie: expected %v got %v", types.EmptyRootHash, actualRoot)
	}
	return actualRoot
}

// compare root hash results to the original GETH implementation
func TestHashRoot(t *testing.T) {
	var (
		testCounts = []int{0, 1, 2, 3, 4, 10, 51, 1000, 126, 127, 128, 129, 130, 765}
		instance   = New()
	)
	for _, count := range testCounts {
		t.Logf("transactions root hash (%4v) %v", count, verifyRootHash(t, instance, generateTransactions(count)))
	}
	for _, count := range testCounts {
		t.Logf("receipts root hash (%4v) %v", count, verifyRootHash(t, instance, generateReceipts(count)))
	}
}
