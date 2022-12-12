package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/ethereum/go-ethereum/core"
	"github.com/ethereum/go-ethereum/core/types"
	"github.com/ethereum/go-ethereum/core/vm"
	"github.com/ethereum/go-ethereum/crypto"
	"github.com/ethereum/go-ethereum/eth/tracers/logger"
	"github.com/ethereum/go-ethereum/params"
	"libevm/lib/geth_internal"
	"math"
	"math/big"
	"time"
)

type EvmParams struct {
	HandleParams
	From         common.Address   `json:"from"`
	To           *common.Address  `json:"to"`
	Value        *hexutil.Big     `json:"value"`
	Input        []byte           `json:"input"`
	AvailableGas hexutil.Uint64   `json:"availableGas"`
	GasPrice     *hexutil.Big     `json:"gasPrice"`
	AccessList   types.AccessList `json:"accessList"`
	Context      EvmContext       `json:"context"`
	TraceOptions *TraceOptions    `json:"traceOptions"`
}

type EvmContext struct {
	ChainID     hexutil.Uint64 `json:"chainID"`
	Coinbase    common.Address `json:"coinbase"`
	GasLimit    hexutil.Uint64 `json:"gasLimit"`
	BlockNumber *hexutil.Big   `json:"blockNumber"`
	Time        *hexutil.Big   `json:"time"`
	BaseFee     *hexutil.Big   `json:"baseFee"`
	Random      *common.Hash   `json:"random"`
}

type TraceOptions struct {
	EnableMemory     bool `json:"enableMemory"`
	DisableStack     bool `json:"disableStack"`
	DisableStorage   bool `json:"disableStorage"`
	EnableReturnData bool `json:"enableReturnData"`
}

// setDefaults for parameters that were omitted
func (p *EvmParams) setDefaults() {
	if p.Value == nil {
		p.Value = (*hexutil.Big)(common.Big0)
	}
	if p.AvailableGas == 0 {
		p.AvailableGas = (hexutil.Uint64)(math.MaxInt64)
	}
	if p.GasPrice == nil {
		p.GasPrice = (*hexutil.Big)(common.Big0)
	}
	p.Context.setDefaults()
}

// setDefaults for parameters that were omitted
func (c *EvmContext) setDefaults() {
	if c.GasLimit == 0 {
		c.GasLimit = (hexutil.Uint64)(math.MaxInt64)
	}
	if c.BlockNumber == nil {
		c.BlockNumber = (*hexutil.Big)(common.Big0)
	}
	if c.Time == nil {
		c.Time = (*hexutil.Big)(big.NewInt(time.Now().Unix()))
	}
	if c.BaseFee == nil {
		c.BaseFee = (*hexutil.Big)(big.NewInt(params.InitialBaseFee))
	}
	if c.Random == nil {
		c.Random = new(common.Hash)
	}
}

func (c *EvmContext) getBlockContext() vm.BlockContext {
	return vm.BlockContext{
		CanTransfer: core.CanTransfer,
		Transfer:    core.Transfer,
		GetHash:     mockBlockHashFn,
		Coinbase:    c.Coinbase,
		GasLimit:    uint64(c.GasLimit),
		BlockNumber: c.BlockNumber.ToInt(),
		Time:        c.Time.ToInt(),
		Difficulty:  common.Big0,
		BaseFee:     c.BaseFee.ToInt(),
		Random:      c.Random,
	}
}

func (c *EvmContext) getChainConfig() *params.ChainConfig {
	return &params.ChainConfig{
		ChainID:             new(big.Int).SetUint64(uint64(c.ChainID)),
		HomesteadBlock:      common.Big0,
		DAOForkBlock:        nil,
		DAOForkSupport:      false,
		EIP150Block:         common.Big0,
		EIP155Block:         common.Big0,
		EIP158Block:         common.Big0,
		ByzantiumBlock:      common.Big0,
		ConstantinopleBlock: common.Big0,
		PetersburgBlock:     common.Big0,
		IstanbulBlock:       common.Big0,
		MuirGlacierBlock:    common.Big0,
		BerlinBlock:         common.Big0,
		LondonBlock:         common.Big0,
	}
}

func (t *TraceOptions) getTracer() *logger.StructLogger {
	if t == nil {
		return nil
	}
	traceConfig := logger.Config{
		EnableMemory:     t.EnableMemory,
		DisableStack:     t.DisableStack,
		DisableStorage:   t.DisableStorage,
		EnableReturnData: t.EnableReturnData,
	}
	return logger.NewStructLogger(&traceConfig)
}

func mockBlockHashFn(n uint64) common.Hash {
	// TODO: fetch real block hashes
	return common.BytesToHash(crypto.Keccak256([]byte(new(big.Int).SetUint64(n).String())))
}

type EvmResult struct {
	UsedGas         uint64                       `json:"usedGas"`
	EvmError        string                       `json:"evmError"`
	ReturnData      []byte                       `json:"returnData"`
	ContractAddress *common.Address              `json:"contractAddress"`
	TraceLogs       []geth_internal.StructLogRes `json:"traceLogs,omitempty"`
	Reverted        bool                         `json:"reverted"`
}

func (s *Service) EvmApply(params EvmParams) (error, *EvmResult) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, nil
	}

	// apply defaults to missing parameters
	params.setDefaults()

	var (
		txContext = vm.TxContext{
			Origin:   params.From,
			GasPrice: params.GasPrice.ToInt(),
		}
		blockContext = params.Context.getBlockContext()
		chainConfig  = params.Context.getChainConfig()
		tracer       = params.TraceOptions.getTracer()
		evmConfig    = vm.Config{
			Debug:                   tracer != nil,
			Tracer:                  tracer,
			NoBaseFee:               false,
			EnablePreimageRecording: false,
			JumpTable:               nil,
			ExtraEips:               nil,
		}
		evm              = vm.NewEVM(blockContext, txContext, statedb, chainConfig, evmConfig)
		sender           = vm.AccountRef(params.From)
		gas              = uint64(params.AvailableGas)
		contractCreation = params.To == nil
	)

	// Set up the initial access list.
	if rules := evm.ChainConfig().Rules(evm.Context.BlockNumber, evm.Context.Random != nil); rules.IsBerlin {
		statedb.PrepareAccessList(params.From, params.To, vm.ActivePrecompiles(rules), params.AccessList)
	}
	var (
		returnData      []byte
		vmerr           error
		contractAddress *common.Address
	)
	if contractCreation {
		// The following nonce modification is a workaround for the following problem:
		//
		// Creating a smart contract should increment the callers' nonce, this is true for EOAs as well as contracts
		// creating other contracts. Thus, the nonce increment is done in evm.Create and must be there.
		// In contrast to that behavior, for the top level call the nonce was already increased by the SDK at this
		// point. So if we don't do anything here the nonce of an EOA will be increased twice when a smart contract is
		// deployed.
		//
		// As the contract address is calculated from the nonce we can't just decrement the nonce afterwards (to undo
		// the unwanted change), we have to do that before running the EVM. This also introduces two edge cases:
		//
		// - The check nonce > 0 was necessary in an earlier version where the nonce was NOT increased when the call was
		//   performed in the context of eth_call via RPC. This is fixed now, but we should still keep this as a
		//   precaution (this would cause unsigned integer underflow to maxUint64) and because it is useful for tests.
		//
		// - The EVM.create call can fail before it even reaches the point of incrementing the nonce. We have to make
		//   sure to NOT decrement the nonce in that case. Hence, setting the nonce to the value before the EVM call in
		//   case it was modified.
		nonce := statedb.GetNonce(params.From)
		if nonce > 0 {
			statedb.SetNonce(params.From, nonce-1)
		}
		// we ignore returnData here because it holds the contract code that was just deployed
		var deployedContractAddress common.Address
		_, deployedContractAddress, gas, vmerr = evm.Create(sender, params.Input, gas, params.Value.ToInt())
		contractAddress = &deployedContractAddress
		// if there is an error evm.Create might not have incremented the nonce as expected,
		if statedb.GetNonce(params.From) != nonce {
			statedb.SetNonce(params.From, nonce)
		}
	} else {
		returnData, gas, vmerr = evm.Call(sender, *params.To, params.Input, gas, params.Value.ToInt())
	}

	// no error means successful transaction, otherwise failure
	evmError := ""
	if vmerr != nil {
		evmError = vmerr.Error()
	}

	var traceLogs []geth_internal.StructLogRes
	if tracer != nil {
		traceLogs = geth_internal.FormatLogs(tracer.StructLogs())
	}

	return nil, &EvmResult{
		UsedGas:         uint64(params.AvailableGas) - gas,
		EvmError:        evmError,
		ReturnData:      returnData,
		ContractAddress: contractAddress,
		TraceLogs:       traceLogs,
		Reverted:        vmerr == vm.ErrExecutionReverted,
	}
}
