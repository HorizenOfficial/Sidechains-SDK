package interop

import (
	_ "embed"
	"encoding/json"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"libevm/lib"
	"math/big"
	"testing"
)

//go:generate solc --bin --hashes --opcodes --storage-layout --optimize -o compiled --overwrite ../contracts/Storage.sol
var (
	//go:embed compiled/Storage.bin
	storageContractDeploy string
)

func call(t *testing.T, instance *lib.Service, method string, args interface{}) interface{} {
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
		t.Errorf("invocation failed: %v", err)
	}
	t.Log("response", toJsonResponse(err, result))
	return result
}

func TestInvoke(t *testing.T) {
	var (
		instance     = lib.New()
		user         = common.HexToAddress("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b")
		emptyHash    = common.HexToHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
		initialValue = "0000000000000000000000000000000000000000000000000000000000000000"
		anotherValue = "00000000000000000000000000000000000000000000000000000000000015b3"
		funcStore    = "6057361d"
		//funcRetrieve = "2e64cec1"
	)

	dbHandle := call(t, instance, "OpenLevelDB", lib.LevelDBParams{Path: t.TempDir()}).(int)
	handle := call(t, instance, "StateOpen", lib.StateParams{
		DatabaseParams: lib.DatabaseParams{DatabaseHandle: dbHandle},
		Root:           emptyHash,
	}).(int)
	call(t, instance, "StateAddBalance", lib.BalanceParams{
		AccountParams: lib.AccountParams{
			HandleParams: lib.HandleParams{Handle: handle},
			Address:      user,
		},
		Amount: (*hexutil.Big)(big.NewInt(1000000000000000000)),
	})
	call(t, instance, "EvmApply", lib.EvmParams{
		HandleParams: lib.HandleParams{Handle: handle},
		From:         user,
		To:           nil,
		Input:        common.Hex2Bytes(storageContractDeploy + initialValue),
		Nonce:        0,
		GasLimit:     200000,
		GasPrice:     (*hexutil.Big)(big.NewInt(1000000000)),
	})
	contractAddress := common.HexToAddress("0x6F8C38b30Df9967a414543a1338D4497f2570775")
	call(t, instance, "EvmApply", lib.EvmParams{
		HandleParams: lib.HandleParams{Handle: handle},
		From:         user,
		To:           &contractAddress,
		Input:        common.Hex2Bytes(funcStore + anotherValue),
		Nonce:        1,
		GasLimit:     200000,
		GasPrice:     (*hexutil.Big)(big.NewInt(1000000000)),
	})
	call(t, instance, "CloseDatabase", lib.DatabaseParams{
		DatabaseHandle: dbHandle,
	})
}
