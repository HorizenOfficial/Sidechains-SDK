package lib

import (
	"bytes"
	"encoding/json"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/ethereum/go-ethereum/core/types"
	"github.com/ethereum/go-ethereum/crypto"
	"github.com/ethereum/go-ethereum/signer/core/apitypes"
	"github.com/ethereum/go-ethereum/trie"
	"math/big"
	"testing"
)

func generateTransactions(count int) types.Transactions {
	txs := make(types.Transactions, count)
	var (
		to       = common.NewMixedcaseAddress(common.HexToAddress("0x1337"))
		gas      = hexutil.Uint64(21000)
		gasPrice = (hexutil.Big)(*big.NewInt(2000000000))
		data     = hexutil.Bytes(common.FromHex("01020304050607080a"))
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
		tx.GasPrice = &gasPrice
		txs[v] = tx.ToTransaction()
	}
	return txs
}

func generateReceipts(count int) types.Receipts {
	receipts := make(types.Receipts, count)
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
			Logs: []*types.Log{
				{
					Address: common.HexToAddress("1122334455667788990011223344556677889900"),
					Topics: []common.Hash{
						crypto.Keccak256Hash(common.FromHex("aabbccdd")),
					},
					Data: common.FromHex("aabbccdd"),
				},
			},
		}
		// Set the receipt logs and create the bloom filter.
		receipt.Bloom = types.CreateBloom(types.Receipts{receipt})
		// These four are non-consensus fields:
		//receipt.BlockHash
		//receipt.BlockNumber
		//receipt.TxHash = crypto.Keccak256Hash(big.NewInt(int64(41271*count + v)).Bytes())
		receipt.TransactionIndex = uint(v)
		receipts[v] = receipt
	}
	return receipts
}

func verifyRootHash(t *testing.T, instance *Service, list types.DerivableList) common.Hash {
	var (
		length       = list.Len()
		expectedRoot = types.DeriveSha(list, trie.NewStackTrie(nil))
		valueBuf     = new(bytes.Buffer)
		values       = make([][]byte, length)
	)
	// RLP encode receipts
	for i := 0; i < length; i++ {
		valueBuf.Reset()
		list.EncodeIndex(i, valueBuf)
		values[i] = common.CopyBytes(valueBuf.Bytes())
	}
	actualRoot := instance.HashRoot(HashParams{
		Values: values,
	})
	if actualRoot != expectedRoot {
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
		testCounts        = []int{0, 1, 2, 3, 4, 10, 51, 1000, 126, 127, 128, 129, 130, 765}
		instance          = New()
		transactionHashes = make(map[int]common.Hash, 0)
		receiptHashes     = make(map[int]common.Hash, 0)
	)
	// test transaction roots
	for _, count := range testCounts {
		txHash := verifyRootHash(t, instance, generateTransactions(count))
		transactionHashes[count] = txHash
	}
	prettyTxHashes, _ := json.MarshalIndent(transactionHashes, "", "  ")
	t.Logf("transactions root hashes %v", string(prettyTxHashes))
	// test receipt roots
	for _, count := range testCounts {
		receiptHash := verifyRootHash(t, instance, generateReceipts(count))
		receiptHashes[count] = receiptHash
	}
	prettyReceiptHashes, _ := json.MarshalIndent(receiptHashes, "", "  ")
	t.Logf("receipt root hashes %v", string(prettyReceiptHashes))
}
