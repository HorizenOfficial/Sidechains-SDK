#!/usr/bin/env python3
import logging

from eth_utils import to_checksum_address

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import contract_function_static_call, contract_function_call, \
    generate_block_and_get_tx_receipt, random_byte_string, deploy_smart_contract
from test_framework.util import assert_equal

"""
Check an EVM Contract which deploys smart contracts, and their interaction.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the smart contract:
        - Deploy the smart contract with initial secret
        - Deploy a child contract
        - Read the secret via child
        - Deploy a second child contract
        - Read the secret via second child
        - Update secret via second child
        - Read the secret via first and second child
"""


class SCEvmDeployingContract(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=100)

    def __assert_deploy_child(self, smart_contract, smart_contract_address):
        sc_node = self.sc_nodes[0]
        method = 'deployContract()'

        res = contract_function_static_call(sc_node, smart_contract, smart_contract_address, self.evm_address, method)
        assert_equal(0, len(res))

        tx_hash = contract_function_call(sc_node, smart_contract, smart_contract_address, self.evm_address, method)
        tx_status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_equal(1, tx_status, "Transaction failed for some reason")

    def __assert_child_count_and_return(self, smart_contract, smart_contract_address, number_of_children):
        method = 'getChildren()'
        logging.info("Comparing child count...")
        children = list(
            contract_function_static_call(self.sc_nodes[0], smart_contract, smart_contract_address, self.evm_address,
                                          method)[0])
        logging.info("Expected count: {}, actual count: {}".format(number_of_children, children))
        assert_equal(number_of_children, len(children))
        return children

    def __assert_secret(self, smart_contract, smart_contract_address, expected_secret):
        method = 'checkParentSecret()'
        logging.info("Comparing secrets...")
        res = contract_function_static_call(self.sc_nodes[0], smart_contract, smart_contract_address, self.evm_address,
                                            method)[0]
        logging.info("Expected secret: {}, actual secret: {}".format(expected_secret, res))
        assert_equal(expected_secret, res)

    def run_test(self):
        self.sc_ac_setup()
        sc_node = self.sc_nodes[0]

        parent_smart_contract_type = 'TestDeployingContract'
        logging.info(f"Creating smart contract utilities for {parent_smart_contract_type}")
        parent_smart_contract_type = SmartContract(parent_smart_contract_type)
        logging.info(parent_smart_contract_type)

        child_smart_contract_type = 'TestDeployedContract'
        logging.info(f"Creating smart contract utilities for {child_smart_contract_type}")
        child_smart_contract_type = SmartContract(child_smart_contract_type)
        logging.info(child_smart_contract_type)

        initial_secret = random_byte_string(length=20)
        number_of_children = 0

        # testing deployment
        evm_hex_address = to_checksum_address(self.evm_address)
        parent_contract_address = deploy_smart_contract(sc_node, parent_smart_contract_type, evm_hex_address,
                                                        initial_secret)

        # asserting that there are no initial children
        self.__assert_child_count_and_return(parent_smart_contract_type, parent_contract_address, number_of_children)

        # DEPLOYING FIRST CHILD
        self.__assert_deploy_child(parent_smart_contract_type, parent_contract_address)
        number_of_children += 1

        # asserting that there is one child now
        children = self.__assert_child_count_and_return(parent_smart_contract_type, parent_contract_address,
                                                        number_of_children)
        self.__assert_secret(child_smart_contract_type, children[-1], initial_secret)

        # DEPLOYING SECOND CHILD
        self.__assert_deploy_child(parent_smart_contract_type, parent_contract_address)
        number_of_children += 1

        # asserting that there are two children now
        children = self.__assert_child_count_and_return(parent_smart_contract_type, parent_contract_address,
                                                        number_of_children)
        self.__assert_secret(child_smart_contract_type, children[-1], initial_secret)

        # SETTING SECRET VIA SECOND CHILD, CONFIRMING CHANGE VIA FIRST CHILD
        new_secret = random_byte_string()

        method = 'setParentSecret(string)'
        res = contract_function_static_call(sc_node, child_smart_contract_type, children[-1], self.evm_address, method,
                                            new_secret)
        assert_equal(0, len(res))

        tx_hash = contract_function_call(sc_node, child_smart_contract_type, children[-1], self.evm_address, method,
                                         new_secret)
        tx_status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_equal(1, tx_status, "Transaction failed for some reason")

        self.__assert_secret(child_smart_contract_type, children[-1], new_secret)
        self.__assert_secret(child_smart_contract_type, children[0], new_secret)


if __name__ == "__main__":
    SCEvmDeployingContract().main()
