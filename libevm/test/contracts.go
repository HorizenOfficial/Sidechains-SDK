package test

import (
	_ "embed"
	"github.com/ethereum/go-ethereum/common"
	"math/big"
)

//go:generate solc --bin --bin-runtime --hashes --opcodes --storage-layout --optimize -o compiled --overwrite Storage.sol
var (
	//go:embed compiled/Storage.bin
	storageContractDeployCode string
	//go:embed compiled/Storage.bin-runtime
	storageContractRuntimeCode string
)

const (
	storageSignatureStore    = "6057361d"
	storageSignatureRetrieve = "2e64cec1"
)

func StorageContractDeploy(initialValue *big.Int) []byte {
	return append(common.Hex2Bytes(storageContractDeployCode), common.BigToHash(initialValue).Bytes()...)
}

func StorageContractRuntimeCode() []byte {
	return common.Hex2Bytes(storageContractRuntimeCode)
}

func StorageContractStore(value *big.Int) []byte {
	return append(common.Hex2Bytes(storageSignatureStore), common.BigToHash(value).Bytes()...)
}

func StorageContractRetrieve() []byte {
	return common.Hex2Bytes(storageSignatureRetrieve)
}
