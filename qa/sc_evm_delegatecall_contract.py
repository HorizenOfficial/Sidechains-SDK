#!/usr/bin/env python3
import logging

from eth_utils import to_checksum_address

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import contract_function_static_call, contract_function_call, \
    generate_block_and_get_tx_receipt, random_byte_string, deploy_smart_contract
from SidechainTestFramework.scutil import generate_next_block
from test_framework.util import assert_equal, assert_true

#TODO: test is passing, but the original gas calculation algorithm doesn't work. Need to be fixed.
"""
Check an EVM Contract with delegatecall command, which should work properly.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    - Compile the contract
    - Deploy contract B
    - Call setVars method of contract B, use “123” as argument
    - Deploy contract A
    - Copy Contract B address
    - Call setVars method of contract A, use contract B address as the first argument, “456” as the second one
    - Call num method of both contracts
Expected result:
    A: num = 123
    B: num = 456 
"""


class SCEvmDeployingContract(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=100)

    def run_test(self):
        self.sc_ac_setup()
        sc_node = self.sc_nodes[0]

        receiver_contract_type = 'DelegateReceiver'
        logging.info(f"Creating smart contract utilities for {receiver_contract_type}")
        receiver_contract_type = SmartContract(receiver_contract_type)
        logging.info(receiver_contract_type)

        caller_contract_type = 'DelegateCaller'
        logging.info(f"Creating smart contract utilities for {caller_contract_type}")
        caller_contract_type = SmartContract(caller_contract_type)
        logging.info(caller_contract_type)

        initial_secret = random_byte_string(length=20)

        # testing deployment
        evm_hex_address = to_checksum_address(self.evm_address)
        receiver_contract_address = deploy_smart_contract(sc_node, receiver_contract_type, evm_hex_address,
                                                        initial_secret)

        # Test Contract B store-read cycle
        method = 'setVars(uint256)'
        tx_id = contract_function_call(sc_node, receiver_contract_type, receiver_contract_address, self.evm_address,
                                       method, 123)
        block_id = generate_next_block(sc_node, "scnode")
        block_data = sc_node.block_findById(blockId=block_id)["result"]["block"]
        # get block id and check that tx is included
        assert_true(any(x for x in block_data['sidechainTransactions'] if "0x" + x['id'] == tx_id),
                    "Block does not contain transaction with contract call")
        stored_value = contract_function_static_call(sc_node, receiver_contract_type, receiver_contract_address,
                                                     self.evm_address, 'num()')
        assert_equal(123, stored_value[0])

        caller_contract_address = deploy_smart_contract(sc_node, caller_contract_type, evm_hex_address,
                                                       initial_secret)
        # Test Contract A store-read cycle with delegatecall

        method = 'setVars(address,uint256)'

        tx_id = contract_function_call(sc_node, caller_contract_type, caller_contract_address, self.evm_address,
                                       method, receiver_contract_address, 456, value=0, overrideGas=100000)
        block_id = generate_next_block(sc_node, "scnode")
        block_data = sc_node.block_findById(blockId=block_id)["result"]["block"]
        # get block id and check that tx is included
        assert_true(any(x for x in block_data['sidechainTransactions'] if "0x" + x['id'] == tx_id),
                    "Block does not contain transaction with contract call")
        stored_value = contract_function_static_call(sc_node, caller_contract_type, caller_contract_address,
                                                     self.evm_address, 'num()')
        assert_equal(456, stored_value[0])


if __name__ == "__main__":
    SCEvmDeployingContract().main()
