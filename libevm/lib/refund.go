package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/ethereum/go-ethereum/core/state"
	gethParams "github.com/ethereum/go-ethereum/params"
)

// setStateRefunds replicates the refund part of the original SSTORE gas consumption logic.
// Original implementation can be found in go-ethereum, function "gasSStoreEIP2200":
// github.com/ethereum/go-ethereum@v1.10.26/core/vm/gas_table.go:178
//
// This replicates gas refund logic including EIP3529.
//
// 0. If *gasleft* is less than or equal to 2300, fail the current call.
// 1. If current value equals new value (this is a no-op), SLOAD_GAS is deducted.
// 2. If current value does not equal new value:
//   2.1. If original value equals current value (this storage slot has not been changed by the current execution context):
//     2.1.1. If original value is 0, SSTORE_SET_GAS (20K) gas is deducted.
//     2.1.2. Otherwise, SSTORE_RESET_GAS gas is deducted. If new value is 0, add SSTORE_CLEARS_SCHEDULE to refund counter.
//   2.2. If original value does not equal current value (this storage slot is dirty), SLOAD_GAS gas is deducted. Apply both of the following clauses:
//     2.2.1. If original value is not 0:
//       2.2.1.1. If current value is 0 (also means that new value is not 0), subtract SSTORE_CLEARS_SCHEDULE gas from refund counter.
//       2.2.1.2. If new value is 0 (also means that current value is not 0), add SSTORE_CLEARS_SCHEDULE gas to refund counter.
//     2.2.2. If original value equals new value (this storage slot is reset):
//       2.2.2.1. If original value is 0, add SSTORE_SET_GAS - SLOAD_GAS to refund counter.
//       2.2.2.2. Otherwise, add SSTORE_RESET_GAS - SLOAD_GAS gas to refund counter.
func setStateRefunds(statedb *state.StateDB, address common.Address, key, value common.Hash) {
	current := statedb.GetState(address, key)
	// no change, no refund
	if current == value {
		return
	}
	original := statedb.GetCommittedState(address, key)
	if original == current {
		if original == (common.Hash{}) { // create slot (2.1.1)
			return
		}
		if value == (common.Hash{}) { // delete slot (2.1.2b)
			statedb.AddRefund(gethParams.SstoreClearsScheduleRefundEIP3529)
			return
		}
		return // write existing slot (2.1.2)
	}
	if original != (common.Hash{}) {
		if current == (common.Hash{}) { // recreate slot (2.2.1.1)
			statedb.SubRefund(gethParams.SstoreClearsScheduleRefundEIP3529)
		} else if value == (common.Hash{}) { // delete slot (2.2.1.2)
			statedb.AddRefund(gethParams.SstoreClearsScheduleRefundEIP3529)
		}
	}
	if original == value {
		if original == (common.Hash{}) { // reset to original inexistent slot (2.2.2.1)
			statedb.AddRefund(gethParams.SstoreSetGasEIP2200 - gethParams.WarmStorageReadCostEIP2929)
		} else { // reset to original existing slot (2.2.2.2)
			statedb.AddRefund((gethParams.SstoreResetGasEIP2200 - gethParams.ColdSloadCostEIP2929) - gethParams.WarmStorageReadCostEIP2929)
		}
	}
	return // dirty update (2.2)
}

func (s *Service) setStateWithRefund(statedb *state.StateDB, address common.Address, key, value common.Hash) {
	setStateRefunds(statedb, address, key, value)
	statedb.SetState(address, key, value)
}

func (s *Service) StateGetRefund(params HandleParams) (error, hexutil.Uint64) {
	err, statedb := s.statedbs.Get(params.Handle)
	if err != nil {
		return err, 0
	}
	return nil, (hexutil.Uint64)(statedb.GetRefund())
}
