package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/rlp"
	"github.com/ethereum/go-ethereum/trie"
)

type HashParams struct {
	Values [][]byte `json:"values"`
}

// HashRoot returns the root hash of the merkle trie after adding all the given values using their index as the key
// heavily based on types.Derive(), see https://github.com/ethereum/go-ethereum/blob/d12b1a91cd9423f83bf77dbe363164797549ff15/core/types/hashing.go#L87
func (s *Service) HashRoot(params HashParams) (error, common.Hash) {
	hasher := trie.NewStackTrie(nil)
	count := len(params.Values)
	// StackTrie requires values to be inserted in increasing hash order, which is not the
	// order that `list` provides hashes in. This insertion sequence ensures that the
	// order is correct.
	var indexBuf []byte
	for i := 1; i < count && i <= 0x7f; i++ {
		indexBuf = rlp.AppendUint64(indexBuf[:0], uint64(i))
		hasher.Update(indexBuf, params.Values[i])
	}
	if count > 0 {
		indexBuf = rlp.AppendUint64(indexBuf[:0], 0)
		hasher.Update(indexBuf, params.Values[0])
	}
	for i := 0x80; i < count; i++ {
		indexBuf = rlp.AppendUint64(indexBuf[:0], uint64(i))
		hasher.Update(indexBuf, params.Values[i])
	}
	return nil, hasher.Hash()
}
