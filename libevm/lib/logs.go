package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/core/types"
)

type GetLogsParams struct {
	HandleParams
	TxHash common.Hash `json:"txHash"`
}

type AddLogParams struct {
	AccountParams
	Topics []common.Hash `json:"topics"`
	Data   []byte        `json:"data"`
}

type SetTxContextParams struct {
	HandleParams
	TxHash  common.Hash `json:"txHash"`
	TxIndex int         `json:"txIndex"`
}

// Log is a reduced representation of the geth Log type. It only contains the consensus fields and no derived data.
type Log struct {
	// address of the contract that generated the event
	Address common.Address `json:"address"`
	// list of topics provided by the contract.
	Topics []common.Hash `json:"topics"`
	// supplied by the contract, usually ABI-encoded
	Data []byte `json:"data"`
}

// Get converted logs, if there are any
func getLogs(statedb *state.StateDB, txHash common.Hash) []*Log {
	gethLogs := statedb.GetLogs(txHash, common.Hash{})
	logs := make([]*Log, len(gethLogs))
	for i, log := range gethLogs {
		logs[i] = &Log{
			Address: log.Address,
			Topics:  log.Topics,
			Data:    log.Data,
		}
	}
	return logs
}

func (s *Service) StateGetLogs(params GetLogsParams) (error, []*Log) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, nil
	}
	return nil, getLogs(statedb, params.TxHash)
}

func (s *Service) StateAddLog(params AddLogParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	statedb.AddLog(&types.Log{
		Address: params.Address,
		Topics:  params.Topics,
		Data:    params.Data,
	})
	return nil
}

func (s *Service) StateSetTxContext(params SetTxContextParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	statedb.Prepare(params.TxHash, params.TxIndex)
	return nil
}
