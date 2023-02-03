package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"libevm/test"
	"math/big"
	"testing"
)

func TestInvokeDelegatecall(t *testing.T) {
	var (
		instance   = New()
		user       = common.HexToAddress("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b")
		emptyHash  = common.HexToHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
		test1Value = big.NewInt(123)
		test2Value = big.NewInt(456)
	)

	//=====SETUP=====
	_, dbHandle := instance.OpenLevelDB(LevelDBParams{Path: t.TempDir()})
	_, handle := instance.StateOpen(StateParams{
		DatabaseParams: DatabaseParams{DatabaseHandle: dbHandle},
		Root:           emptyHash,
	})
	_ = instance.StateAddBalance(BalanceParams{
		AccountParams: AccountParams{
			HandleParams: HandleParams{Handle: handle},
			Address:      user,
		},
		Amount: (*hexutil.Big)(big.NewInt(1000000000000000000)),
	})

	_ = instance.StateSetNonce(NonceParams{
		AccountParams: AccountParams{
			HandleParams: HandleParams{Handle: handle},
			Address:      user,
		},
		Nonce: 1,
	})

	//=====CONTRACTS DEPLOY=====
	_, deployReceiverResult := instance.EvmApply(EvmParams{
		HandleParams: HandleParams{Handle: handle},
		From:         user,
		To:           nil,
		Input:        test.DelegateReceiver.Deploy(),
		AvailableGas: 200000,
		GasPrice:     (*hexutil.Big)(big.NewInt(1000000000)),
	})
	if deployReceiverResult.EvmError != "" {
		t.Fatalf("vm error: %v", deployReceiverResult.EvmError)
	}
	_, getReceiverCode := instance.StateGetCode(AccountParams{
		HandleParams: HandleParams{Handle: handle},
		Address:      *deployReceiverResult.ContractAddress,
	})
	if common.Bytes2Hex(test.DelegateReceiver.RuntimeCode()) != common.Bytes2Hex(getReceiverCode) {
		t.Fatalf("deployed code does not match %s", common.Bytes2Hex(getReceiverCode))
	}
	_ = instance.StateSetNonce(NonceParams{
		AccountParams: AccountParams{
			HandleParams: HandleParams{Handle: handle},
			Address:      user,
		},
		Nonce: 2,
	})
	_, deployCallerResult := instance.EvmApply(EvmParams{
		HandleParams: HandleParams{Handle: handle},
		From:         user,
		To:           nil,
		Input:        test.DelegateCaller.Deploy(),
		AvailableGas: 200000,
		GasPrice:     (*hexutil.Big)(big.NewInt(1000000000)),
	})
	if deployCallerResult.EvmError != "" {
		t.Fatalf("vm error: %v", deployCallerResult.EvmError)
	}
	_, getCallerCode := instance.StateGetCode(AccountParams{
		HandleParams: HandleParams{Handle: handle},
		Address:      *deployCallerResult.ContractAddress,
	})
	if common.Bytes2Hex(test.DelegateCaller.RuntimeCode()) != common.Bytes2Hex(getCallerCode) {
		t.Fatalf("deployed code does not match %s", common.Bytes2Hex(getCallerCode))
	}

	//===== TEST 1 =====
	//store value
	_, _ = instance.EvmApply(EvmParams{
		HandleParams: HandleParams{Handle: handle},
		From:         user,
		To:           deployReceiverResult.ContractAddress,
		Input:        test.DelegateReceiver.Store(test1Value),
		AvailableGas: 200000,
		GasPrice:     (*hexutil.Big)(big.NewInt(1000000000)),
	})
	//retrieve value
	_, receiverTestResult := instance.EvmApply(EvmParams{
		HandleParams: HandleParams{Handle: handle},
		From:         user,
		To:           deployReceiverResult.ContractAddress,
		Input:        test.DelegateReceiver.Retrieve(),
		AvailableGas: 200000,
		GasPrice:     (*hexutil.Big)(big.NewInt(1000000000)),
	})
	if receiverTestResult.EvmError != "" {
		t.Fatalf("vm error: %v", receiverTestResult.EvmError)
	}
	receiverTestValue := common.BytesToHash(receiverTestResult.ReturnData).Big()
	if test1Value.Cmp(receiverTestValue) != 0 {
		t.Fatalf("retrieved bad value: expected %v, actual %v", test1Value, receiverTestValue)
	}

	//===== TEST 2 =====
	//store value
	_, _ = instance.EvmApply(EvmParams{
		HandleParams: HandleParams{Handle: handle},
		From:         user,
		To:           deployCallerResult.ContractAddress,
		Input:        test.DelegateCaller.Store(deployReceiverResult.ContractAddress, test2Value),
		AvailableGas: 200000,
		GasPrice:     (*hexutil.Big)(big.NewInt(1000000000)),
		TraceOptions: &TraceOptions{
			EnableMemory:     true,
			DisableStack:     false,
			DisableStorage:   false,
			EnableReturnData: true,
		},
	})
	//retrieve value
	_, callerTestResult := instance.EvmApply(EvmParams{
		HandleParams: HandleParams{Handle: handle},
		From:         user,
		To:           deployCallerResult.ContractAddress,
		Input:        test.DelegateCaller.Retrieve(),
		AvailableGas: 200000,
		GasPrice:     (*hexutil.Big)(big.NewInt(1000000000)),
	})
	if callerTestResult.EvmError != "" {
		t.Fatalf("vm error: %v", callerTestResult.EvmError)
	}
	callerTestValue := common.BytesToHash(callerTestResult.ReturnData).Big()
	if test2Value.Cmp(callerTestValue) != 0 {
		t.Fatalf("retrieved bad value: expected %v, actual %v", test2Value, callerTestValue)
	}

	// verify that EOA nonce was not updated
	_, nonce := instance.StateGetNonce(AccountParams{
		HandleParams: HandleParams{Handle: handle},
		Address:      user,
	})
	if uint64(nonce) != 2 {
		t.Fatalf("nonce was modified: expected 2, actual %v", nonce)
	}
	// cleanup
	_ = instance.CloseDatabase(DatabaseParams{DatabaseHandle: dbHandle})
}
