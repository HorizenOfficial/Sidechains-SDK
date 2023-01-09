package lib

import "github.com/ethereum/go-ethereum/common"

type SlotParams struct {
	AccountParams
	Slot common.Hash
}

func (s *Service) AccessAccount(params AccountParams) (error, bool) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, false
	}
	warmAccount := statedb.AddressInAccessList(params.Address)
	if !warmAccount {
		statedb.AddAddressToAccessList(params.Address)
	}
	return nil, warmAccount
}

func (s *Service) AccessSlot(params SlotParams) (error, bool) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, false
	}
	_, warmSlot := statedb.SlotInAccessList(params.Address, params.Slot)
	if !warmSlot {
		statedb.AddSlotToAccessList(params.Address, params.Slot)
	}
	return nil, warmSlot
}
