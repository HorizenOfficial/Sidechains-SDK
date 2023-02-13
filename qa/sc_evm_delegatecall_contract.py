#!/usr/bin/env python3
import logging

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import (
    contract_function_call, contract_function_static_call, deploy_smart_contract, generate_block_and_get_tx_receipt,
)
from test_framework.util import assert_equal, assert_true

"""
Check an EVM Contract with delegatecall instruction, which should work properly.
Also verify the behavior of gas estimation in case errors in nested calls are not reported by the calling function.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    - Compile and deploy two contracts: DelegateReceiver and DelegateCaller
    - Write using DelegateReceiver
    - Verify value
    - Write using DelegateCaller
    - Verify value is not actually written (out of gas for the internal call)
    - Write again using DelegateCaller and a manually increased gas limit
    - Verify value
    - Write using a modified version "setVarsAssert" which reports errors of the internal call
    - Verify value
"""


class SCEvmDelegateCall(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=100)

    def deploy(self, contract_name):
        logging.info(f"Creating smart contract utilities for {contract_name}")
        contract = SmartContract(contract_name)
        logging.info(contract)
        contract_address = deploy_smart_contract(self.sc_nodes[0], contract, self.evm_address)
        return contract, contract_address

    def write_read_cycle(self, contract, address, write_method, write_args, expected_value, overrideGas=None):
        # write value
        tx_hash = contract_function_call(
            self.sc_nodes[0], contract, address, self.evm_address, write_method, *write_args, overrideGas=overrideGas
        )
        # make sure the transaction was executed successfully
        result = generate_block_and_get_tx_receipt(self.sc_nodes[0], tx_hash, True)
        assert_true(result, "call failed")
        # read value using a static call
        read_value = contract_function_static_call(self.sc_nodes[0], contract, address, self.evm_address, "num()")
        assert_equal(expected_value, read_value[0], "unexpected value")

    def run_test(self):
        self.sc_ac_setup()

        receiver, receiver_address = self.deploy("DelegateReceiver")
        caller, caller_address = self.deploy("DelegateCaller")

        # Write directly to the receiver contract
        # we expect this to work and the given value to be written and then read
        self.write_read_cycle(receiver, receiver_address, "setVars(uint256)", (123,), 123)

        # Write using the Caller Contract and delegatecall
        # This should fail because the implementation of setVars does not report back errors during the delegate-call.
        # The gas estimation will thus result in a gas limit that is too low to successfully execute the delegate-call,
        # but the transaction itself will not be marked as failed. We expect the read value to remain zero.
        self.write_read_cycle(caller, caller_address, "setVars(address,uint256)", (receiver_address, 456), 0)

        # We can make the function work by manually setting a higher gas limit
        self.write_read_cycle(
            caller, caller_address, "setVars(address,uint256)", (receiver_address, 456), 456, overrideGas=80000
        )

        # Write using the Caller Contract and delegatecall
        # This should be successful because the implementation of setVarsAssert will report any errors during the
        # delegate-call and gas estimation will give a value that actually makes the delegate-call work.
        self.write_read_cycle(caller, caller_address, "setVarsAssert(address,uint256)", (receiver_address, 789), 789)


if __name__ == "__main__":
    SCEvmDelegateCall().main()
