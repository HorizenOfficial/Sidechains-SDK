package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/ethereum/go-ethereum/core"
	"github.com/ethereum/go-ethereum/core/types"
	"github.com/ethereum/go-ethereum/core/vm"
	"github.com/ethereum/go-ethereum/crypto"
	"github.com/ethereum/go-ethereum/params"
	"math"
	"math/big"
	"math/rand"
	"time"
)

type EvmParams struct {
	// statedb handle
	HandleParams

	// message to execute
	From     common.Address  `json:"from"`
	To       *common.Address `json:"to"`
	Value    *hexutil.Big    `json:"value"`
	Input    []byte          `json:"input"`
	Nonce    hexutil.Uint64  `json:"nonce"`
	GasLimit hexutil.Uint64  `json:"gasLimit"`
	GasPrice *hexutil.Big    `json:"gasPrice"`

	// context parameters
	Difficulty  *hexutil.Big   `json:"difficulty"`
	Coinbase    common.Address `json:"coinbase"`
	BlockNumber *hexutil.Big   `json:"blockNumber"`
	Time        *hexutil.Big   `json:"time"`
	BaseFee     *hexutil.Big   `json:"baseFee"`
}

// setDefaults for parameters that were omitted
func (p *EvmParams) setDefaults() {
	if p.Value == nil {
		p.Value = (*hexutil.Big)(new(big.Int))
	}
	if p.GasLimit == 0 {
		p.GasLimit = (hexutil.Uint64)(math.MaxUint64)
	}
	if p.GasPrice == nil {
		p.GasPrice = (*hexutil.Big)(new(big.Int))
	}
	if p.Difficulty == nil {
		p.Difficulty = (*hexutil.Big)(new(big.Int))
	}
	if p.BlockNumber == nil {
		p.BlockNumber = (*hexutil.Big)(new(big.Int))
	}
	if p.Time == nil {
		p.Time = (*hexutil.Big)(big.NewInt(time.Now().Unix()))
	}
	if p.BaseFee == nil {
		p.BaseFee = (*hexutil.Big)(big.NewInt(params.InitialBaseFee))
	}
}

func (p *EvmParams) getMessage() types.Message {
	return types.NewMessage(p.From, p.To, uint64(p.Nonce), p.Value.ToInt(), uint64(p.GasLimit), p.GasPrice.ToInt(), p.GasPrice.ToInt(), p.GasPrice.ToInt(), p.Input, nil, false)
}

func (p *EvmParams) getBlockContext() vm.BlockContext {
	return vm.BlockContext{
		CanTransfer: core.CanTransfer,
		Transfer:    core.Transfer,
		GetHash:     mockBlockHashFn,
		Coinbase:    p.Coinbase,
		BlockNumber: p.BlockNumber.ToInt(),
		Time:        p.Time.ToInt(),
		Difficulty:  p.Difficulty.ToInt(),
		GasLimit:    uint64(p.GasLimit),
		BaseFee:     p.BaseFee.ToInt(),
	}
}

func mockBlockHashFn(n uint64) common.Hash {
	return common.BytesToHash(crypto.Keccak256([]byte(new(big.Int).SetUint64(n).String())))
}

func defaultChainConfig() *params.ChainConfig {
	return &params.ChainConfig{
		ChainID:             big.NewInt(1),
		HomesteadBlock:      new(big.Int),
		DAOForkBlock:        new(big.Int),
		DAOForkSupport:      false,
		EIP150Block:         new(big.Int),
		EIP150Hash:          common.Hash{},
		EIP155Block:         new(big.Int),
		EIP158Block:         new(big.Int),
		ByzantiumBlock:      new(big.Int),
		ConstantinopleBlock: new(big.Int),
		PetersburgBlock:     new(big.Int),
		IstanbulBlock:       new(big.Int),
		MuirGlacierBlock:    new(big.Int),
		BerlinBlock:         new(big.Int),
		LondonBlock:         new(big.Int),
	}
}

type EvmResult struct {
	UsedGas         uint64          `json:"usedGas"`
	EvmError        string          `json:"evmError"`
	ReturnData      []byte          `json:"returnData"`
	ContractAddress *common.Address `json:"contractAddress"`
	Logs            []*Log          `json:"logs"`
}

func (s *Service) EvmApply(params EvmParams) (error, *EvmResult) {
	err, statedb := s.getState(params.Handle)
	if err != nil {
		return err, nil
	}

	// apply default to missing parameters
	params.setDefaults()

	var (
		msg          = params.getMessage()
		txContext    = core.NewEVMTxContext(msg)
		blockContext = params.getBlockContext()
		chainConfig  = defaultChainConfig()
		evmConfig    = vm.Config{
			Debug:                   false,
			Tracer:                  nil,
			NoBaseFee:               false,
			EnablePreimageRecording: false,
			JumpTable:               nil,
			ExtraEips:               nil,
		}
		evm            = vm.NewEVM(blockContext, txContext, statedb, chainConfig, evmConfig)
		txHash         = common.Hash{}
		txIndexInBlock = 0
	)
	// TODO: this mocks the tx hash with random, replace with real tx hash
	var (
		randSource    = rand.NewSource(time.Now().UnixNano())
		randGenerator = rand.New(randSource)
	)
	randGenerator.Read(txHash.Bytes())

	gasPool := new(core.GasPool).AddGas(msg.Gas())
	statedb.Prepare(txHash, txIndexInBlock)

	// reference for the following is:
	// https://github.com/ethereum/go-ethereum/blob/5bc4e8f09d7c9369b718b16c1c073070ee758395/core/state_processor.go#L95
	// the actual receipt is expected to be created in the core

	// Apply the transaction to the current state (included in the env).
	result, err := core.ApplyMessage(evm, msg, gasPool)
	if err != nil {
		return err, nil
	}

	// Update the state with pending changes.
	statedb.Finalise(true)

	applyResult := &EvmResult{
		UsedGas: result.UsedGas,
		Logs:    getLogs(statedb, txHash),
	}

	// no error means successful transaction, otherwise failure
	if result.Err != nil {
		applyResult.EvmError = result.Err.Error()
	}

	// If the transaction created a contract, store the creation address in the receipt.
	if msg.To() == nil {
		contractAddress := crypto.CreateAddress(evm.TxContext.Origin, msg.Nonce())
		applyResult.ContractAddress = &contractAddress
	} else {
		applyResult.ReturnData = result.ReturnData
	}

	return nil, applyResult
}
