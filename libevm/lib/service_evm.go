package lib

import (
	"fmt"
	"math"
	"math/big"
	"time"

	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/ethereum/go-ethereum/core"
	"github.com/ethereum/go-ethereum/core/types"
	"github.com/ethereum/go-ethereum/core/vm"
	"github.com/ethereum/go-ethereum/crypto"
	"github.com/ethereum/go-ethereum/params"
)

type EvmContext struct {
	TxHash      common.Hash    `json:"txHash"`
	TxIndex     int            `json:"txIndex"`
	Difficulty  *hexutil.Big   `json:"difficulty"`
	Coinbase    common.Address `json:"coinbase"`
	BlockNumber *hexutil.Big   `json:"blockNumber"`
	Time        *hexutil.Big   `json:"time"`
	BaseFee     *hexutil.Big   `json:"baseFee"`
}

type EvmParams struct {
	HandleParams
	From       common.Address   `json:"from"`
	To         *common.Address  `json:"to"`
	Value      *hexutil.Big     `json:"value"`
	Input      []byte           `json:"input"`
	GasLimit   hexutil.Uint64   `json:"gasLimit"`
	GasPrice   *hexutil.Big     `json:"gasPrice"`
	AccessList types.AccessList `json:"accessList"`
	Context    EvmContext       `json:"context"`
}

// setDefaults for parameters that were omitted
func (c *EvmContext) setDefaults() {
	if c.Difficulty == nil {
		c.Difficulty = (*hexutil.Big)(new(big.Int))
	}
	if c.BlockNumber == nil {
		c.BlockNumber = (*hexutil.Big)(new(big.Int))
	}
	if c.Time == nil {
		c.Time = (*hexutil.Big)(big.NewInt(time.Now().Unix()))
	}
	if c.BaseFee == nil {
		c.BaseFee = (*hexutil.Big)(big.NewInt(params.InitialBaseFee))
	}
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
	p.Context.setDefaults()
}

func (p *EvmParams) getBlockContext() vm.BlockContext {
	return vm.BlockContext{
		CanTransfer: core.CanTransfer,
		Transfer:    core.Transfer,
		GetHash:     mockBlockHashFn,
		Coinbase:    p.Context.Coinbase,
		BlockNumber: p.Context.BlockNumber.ToInt(),
		Time:        p.Context.Time.ToInt(),
		Difficulty:  p.Context.Difficulty.ToInt(),
		GasLimit:    uint64(p.GasLimit),
		BaseFee:     p.Context.BaseFee.ToInt(),
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
}

func (s *Service) EvmApply(params EvmParams) (error, *EvmResult) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, nil
	}

	// apply default to missing parameters
	params.setDefaults()

	var (
		txContext = vm.TxContext{
			Origin:   params.From,
			GasPrice: new(big.Int).Set(params.GasPrice.ToInt()),
		}
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
		evm              = vm.NewEVM(blockContext, txContext, statedb, chainConfig, evmConfig)
		sender           = vm.AccountRef(params.From)
		gas              = uint64(params.GasLimit)
		contractCreation = params.To == nil
		homestead        = evm.ChainConfig().IsHomestead(evm.Context.BlockNumber)
		istanbul         = evm.ChainConfig().IsIstanbul(evm.Context.BlockNumber)
	)

	statedb.Prepare(params.Context.TxHash, params.Context.TxIndex)

	// Check clauses 4-5, subtract intrinsic gas if everything is correct
	intrinsicGas, err := core.IntrinsicGas(params.Input, params.AccessList, contractCreation, homestead, istanbul)
	if err != nil {
		return err, nil
	}
	if gas < intrinsicGas {
		return fmt.Errorf("%w: have %d, want %d", core.ErrIntrinsicGas, gas, intrinsicGas), nil
	}
	gas -= intrinsicGas

	// Check clause 6
	// TODO: do we need this here?
	//if msg.Value().Sign() > 0 && !evm.Context.CanTransfer(statedb, msg.From(), msg.Value()) {
	//	return fmt.Errorf("%w: address %v", core.ErrInsufficientFundsForTransfer, msg.From().Hex()), nil
	//}

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
		var deployedContractAddress common.Address
		// Since we increase the nonce before any transactions, we would get a wrong result here
		// So we reduce the nonce by 1 and then evm.Create will increase it again to the value it should
		// be after the transaction. This is necessary, since in the non-creation case the nonce should
		// already be increased
		statedb.SetNonce(params.From, statedb.GetNonce(params.From)-1)
		// we ignore returnData here because it holds the contract code that was just deployed
		_, deployedContractAddress, gas, vmerr = evm.Create(sender, params.Input, gas, params.Value.ToInt())
		// TODO check if the nonce could ever be wrong here in an error case for example
		contractAddress = &deployedContractAddress
	} else {
		returnData, gas, vmerr = evm.Call(sender, *params.To, params.Input, gas, params.Value.ToInt())
	}

	// no error means successful transaction, otherwise failure
	evmError := ""
	if vmerr != nil {
		evmError = vmerr.Error()
	}

	return nil, &EvmResult{
		UsedGas:         uint64(params.GasLimit) - gas,
		EvmError:        evmError,
		ReturnData:      returnData,
		ContractAddress: contractAddress,
	}
}

// quick implementation
// TODO clean up
func (s *Service) EvmStaticCall(params EvmParams) (error, *EvmResult) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, nil
	}

	params.setDefaults()

	var (
		txContext = vm.TxContext{
			Origin:   params.From,
			GasPrice: new(big.Int).Set(params.GasPrice.ToInt()),
		}
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
		evm              = vm.NewEVM(blockContext, txContext, statedb, chainConfig, evmConfig)
		sender           = vm.AccountRef(params.From)
		gas              = uint64(params.GasLimit)
		contractCreation = params.To == nil
		homestead        = evm.ChainConfig().IsHomestead(evm.Context.BlockNumber)
		istanbul         = evm.ChainConfig().IsIstanbul(evm.Context.BlockNumber)
	)

	statedb.Prepare(params.Context.TxHash, params.Context.TxIndex)

	intrinsicGas, err := core.IntrinsicGas(params.Input, params.AccessList, contractCreation, homestead, istanbul)
	if err != nil {
		return err, nil
	}
	if gas < intrinsicGas {
		return fmt.Errorf("%w: have %d, want %d", core.ErrIntrinsicGas, gas, intrinsicGas), nil
	}
	gas -= intrinsicGas

	if rules := evm.ChainConfig().Rules(evm.Context.BlockNumber, evm.Context.Random != nil); rules.IsBerlin {
		statedb.PrepareAccessList(params.From, params.To, vm.ActivePrecompiles(rules), params.AccessList)
	}

	var (
		returnData      []byte
		vmerr           error
		contractAddress *common.Address
	)

	returnData, gas, vmerr = evm.StaticCall(sender, *params.To, params.Input, gas)

	evmError := ""
	if vmerr != nil {
		evmError = vmerr.Error()
	}

	return nil, &EvmResult{
		UsedGas:         uint64(params.GasLimit) - gas,
		EvmError:        evmError,
		ReturnData:      returnData,
		ContractAddress: contractAddress,
	}
}
