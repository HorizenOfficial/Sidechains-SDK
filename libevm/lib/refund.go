package lib

import (
	"github.com/ethereum/go-ethereum/common/hexutil"
)

type RefundParams struct {
	HandleParams
	Gas hexutil.Uint64 `json:"gas"`
}

func (s *Service) RefundAdd(params RefundParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	statedb.AddRefund(uint64(params.Gas))
	return nil
}

func (s *Service) RefundSub(params RefundParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	statedb.SubRefund(uint64(params.Gas))
	return nil
}

func (s *Service) RefundGet(params HandleParams) (error, hexutil.Uint64) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, 0
	}
	return nil, (hexutil.Uint64)(statedb.GetRefund())
}
