package main

import (
	_ "embed"
	"encoding/json"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/ethereum/go-ethereum/log"
	"libevm/lib"
	"libevm/types"
	"math/big"
	"os"
)

const logLevel = log.LvlDebug

// static initializer
func init() {
	var logger = log.NewGlogHandler(log.StreamHandler(os.Stdout, log.TerminalFormat(true)))
	logger.Verbosity(logLevel)
	log.Root().SetHandler(logger)
}

func test(method string, args interface{}) interface{} {
	jsonArgs := ""
	if args != nil {
		jsonBytes, err := json.Marshal(args)
		if err != nil {
			panic(err)
		}
		jsonArgs = string(jsonBytes)
	}
	err, result := invoke(method, jsonArgs)
	log.Info("invocation", "err", err, "result", result)
	if err != nil {
		panic(err)
	}
	jsonResultBytes, _ := json.Marshal(result)
	jsonResult := string(jsonResultBytes)
	log.Info("invocation", "json", jsonResult)
	return result
}

//go:generate solc --bin --hashes --opcodes --storage-layout --optimize -o contracts/compiled --overwrite contracts/Storage.sol
var (
	//go:embed contracts/compiled/Storage.bin
	storageContractDeploy string
)

// main function is required by cgo, but is never called if this is build as a shared library
// can be used for testing Cgo features if this is build as an executable, as these are not available in unit tests
func main() {
	user := common.HexToAddress("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b")
	emptyHash := common.HexToHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
	initialValue := "0000000000000000000000000000000000000000000000000000000000000000"
	anotherValue := "00000000000000000000000000000000000000000000000000000000000015b3"

	funcStore := "6057361d"
	//funcRetrieve := "2e64cec1"

	test("OpenLevelDB", lib.LevelDBParams{Path: "test-db"})
	handle := test("StateOpen", lib.StateRootParams{Root: emptyHash}).(int)
	test("StateAddBalance", lib.BalanceParams{
		AccountParams: lib.AccountParams{
			HandleParams: lib.HandleParams{
				Handle: handle,
			},
			Address: user,
		},
		Amount: (*hexutil.Big)(big.NewInt(1000000000000000000)),
	})
	test("EvmApply", lib.EvmParams{
		HandleParams: lib.HandleParams{
			Handle: handle,
		},
		Config: types.SerializableConfig{
			Origin:   user,
			GasLimit: 200000,
			GasPrice: (*hexutil.Big)(big.NewInt(1000000000)),
		},
		Address: nil,
		Nonce:   0,
		Input:   common.Hex2Bytes(storageContractDeploy + initialValue),
	})
	contractAddress := common.HexToAddress("0x6F8C38b30Df9967a414543a1338D4497f2570775")
	test("EvmApply", lib.EvmParams{
		HandleParams: lib.HandleParams{
			Handle: handle,
		},
		Config: types.SerializableConfig{
			Origin:   user,
			GasLimit: 200000,
			GasPrice: (*hexutil.Big)(big.NewInt(1000000000)),
		},
		Address: &contractAddress,
		Nonce:   1,
		Input:   common.Hex2Bytes(funcStore + anotherValue),
	})
	test("CloseDatabase", nil)
}
