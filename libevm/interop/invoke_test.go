package interop

import (
	_ "embed"
	"encoding/json"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"libevm/lib"
	"libevm/types"
	"math/big"
	"testing"
)

//go:generate solc --bin --hashes --opcodes --storage-layout --optimize -o compiled --overwrite ../contracts/Storage.sol
var (
	//go:embed compiled/Storage.bin
	storageContractDeploy string
	instance              = lib.New()
)

func call(t *testing.T, method string, args interface{}) interface{} {
	jsonArgs := ""
	if args != nil {
		jsonBytes, err := json.Marshal(args)
		if err != nil {
			panic(err)
		}
		jsonArgs = string(jsonBytes)
	}
	t.Log("invoke", method, jsonArgs)
	err, result := callMethod(instance, method, jsonArgs)
	if err != nil {
		t.Errorf("invocation failed %v %v", err, result)
	}
	response := toJsonResponse(err, result)
	t.Log("response", response)
	return result
}

func TestInvoke(t *testing.T) {
	user := common.HexToAddress("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b")
	emptyHash := common.HexToHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
	initialValue := "0000000000000000000000000000000000000000000000000000000000000000"
	anotherValue := "00000000000000000000000000000000000000000000000000000000000015b3"

	funcStore := "6057361d"
	//funcRetrieve := "2e64cec1"

	call(t, "OpenLevelDB", lib.LevelDBParams{Path: t.TempDir()})
	handle := call(t, "StateOpen", lib.StateRootParams{Root: emptyHash}).(int)
	call(t, "StateAddBalance", lib.BalanceParams{
		AccountParams: lib.AccountParams{
			HandleParams: lib.HandleParams{
				Handle: handle,
			},
			Address: user,
		},
		Amount: (*hexutil.Big)(big.NewInt(1000000000000000000)),
	})
	call(t, "EvmExecute", lib.EvmParams{
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
	call(t, "EvmExecute", lib.EvmParams{
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
	call(t, "CloseDatabase", nil)
}
