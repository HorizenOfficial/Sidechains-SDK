package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/types"
	"github.com/ethereum/go-ethereum/core/vm"
)

type AccessParams struct {
	AccountParams
	Destination *common.Address  `json:"destination"`
	AccessList  types.AccessList `json:"accessList"`
}

type SlotParams struct {
	AccountParams
	Slot common.Hash `json:"slot"`
}

func (s *Service) AccessSetup(params AccessParams) error {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err
	}
	statedb.PrepareAccessList(params.Address, params.Destination, vm.PrecompiledAddressesBerlin, params.AccessList)
	return nil
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
