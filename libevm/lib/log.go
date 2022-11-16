package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/core/types"
)

// Log is a reduced representation of the geth Log type. It only contains the consensus fields and no derived data.
type Log struct {
	// address of the contract that generated the event
	Address common.Address `json:"address"`
	// list of topics provided by the contract.
	Topics []common.Hash `json:"topics"`
	// supplied by the contract, usually ABI-encoded
	Data []byte `json:"data"`
}

func NewLog(log *types.Log) *Log {
	return &Log{
		Address: log.Address,
		Topics:  log.Topics,
		Data:    log.Data,
	}
}

// Get converted logs, if there are any
func getLogs(statedb *state.StateDB, txHash common.Hash) []*Log {
	gethLogs := statedb.GetLogs(txHash, common.Hash{})
	logs := make([]*Log, len(gethLogs))
	for i, log := range gethLogs {
		logs[i] = NewLog(log)
	}
	return logs
}
