package test

import (
	_ "embed"
	"github.com/ethereum/go-ethereum/common"
	"math/big"
)

//go:generate solc --bin --hashes --opcodes --storage-layout --optimize -o compiled --overwrite ../contracts/Storage.sol
var (
	//go:embed compiled/Storage.bin
	storageContractCode string
)

const (
	storageSignatureStore    = "6057361d"
	storageSignatureRetrieve = "2e64cec1"
)

func StorageContractDeploy(initialValue *big.Int) []byte {
	return append(common.Hex2Bytes(storageContractCode), common.BigToHash(initialValue).Bytes()...)
}

func StorageContractStore(value *big.Int) []byte {
	return append(common.Hex2Bytes(storageSignatureStore), common.BigToHash(value).Bytes()...)
}

func StorageContractRetrieve() []byte {
	return common.Hex2Bytes(storageSignatureRetrieve)
}
