#!/usr/bin/env python3
import logging

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import deploy_smart_contract
from SidechainTestFramework.account.utils import FORGER_STAKE_SMART_CONTRACT_ADDRESS
from test_framework.util import assert_equal

"""
Check an EVM Contract calling a native contract.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    - Compile and deploy the NativeInterop contract
    - Fetch all forger stakes via the NativeInterop contract
    - Fetch all forger stakes by calling the native contract directly
    - Verify identical results
"""


class SCEvmNativeInterop(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=100)

    def deploy(self, contract_name):
        logging.info(f"Creating smart contract utilities for {contract_name}")
        contract = SmartContract(contract_name)
        logging.info(contract)
        contract_address = deploy_smart_contract(self.sc_nodes[0], contract, self.evm_address)
        return contract, contract_address

    def run_test(self):
        self.sc_ac_setup()
        node = self.sc_nodes[0]

        # Compile and deploy the NativeInterop contract
        _, contract_address = self.deploy("NativeInterop")

        # Fetch all forger stakes via the NativeInterop contract
        actual_value = node.rpc_eth_call(
            {
                "to": contract_address,
                "input": "0xd9908c86"
            }, "latest"
        )

        # Fetch all forger stakes by calling the native contract directly
        expected_value = node.rpc_eth_call(
            {
                "to": "0x" + FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                "input": "0xf6ad3c23"
            }, "latest"
        )

        # Verify identical results
        assert_equal(expected_value, actual_value, "results do not match")
        logging.info("")


if __name__ == "__main__":
    SCEvmNativeInterop().main()
