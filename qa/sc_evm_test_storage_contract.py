#!/usr/bin/env python3
import logging
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract, EvmExecutionError
from SidechainTestFramework.account.ac_utils import format_evm, generate_block_and_get_tx_receipt
from SidechainTestFramework.scutil import generate_next_blocks
from test_framework.util import assert_equal, assert_true

"""
Check an EVM Storage Smart Contract.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the smart contract:
        - Deploy the smart contract with initial data
        - Check initial value
        - Set the storage to a string
        - Read the string in a read-only call
        - Set the storage to a different string
        - Read the different string in a read only call
"""


class SCEvmStorageContract(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=20)

    def __set_storage_value(self, smart_contract, address, tx_sender, new_value, *, static_call=False,
                            generate_block=True):
        node = self.sc_nodes[0]
        if static_call:
            logging.info("Testing setting smart contract storage to {} in a static call".format(new_value))
            res = smart_contract.static_call(node, 'set(string)', new_value, fromAddress=tx_sender, toAddress=address,
                                             gasPrice=900000000)
        else:
            logging.info("Setting smart contract storage to {}".format(new_value))
            res = smart_contract.call_function(node, 'set(string)', new_value, fromAddress=tx_sender,
                                               gasLimit=10000000, toAddress=address)
        if generate_block:
            logging.info("generating next block...")
            generate_next_blocks(node, "first node", 1)

        if not static_call:
            tx_receipt = node.rpc_eth_getTransactionReceipt(res)
            logging.info(tx_receipt)
            return tx_receipt['result']

        return res

    def __check_storage_value(self, smart_contract, address, tx_sender, expected_value):
        node = self.sc_nodes[0]
        logging.info("Checking stored value...")
        res = smart_contract.static_call(node, 'get()', fromAddress=tx_sender, toAddress=address, gasPrice=900000000)
        logging.info("Expected stored value: \"{}\", actual stored value: \"{}\"".format(expected_value, res[0]))
        assert_equal(expected_value, res[0])

    def run_test(self):
        sc_node = self.sc_nodes[0]
        ft_amount_in_zen = Decimal("3000")
        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        smart_contract_type = 'StorageTestContract'
        logging.info(f"Creating smart contract utilities for {smart_contract_type}")
        smart_contract = SmartContract(smart_contract_type)
        logging.info(smart_contract)
        test_message = 'Initial message'

        estimated_gas = smart_contract.estimate_gas(sc_node, 'constructor', test_message,
                                                    fromAddress=self.evm_address)
        tx_hash, smart_contract_address = smart_contract.deploy(sc_node, test_message,
                                                                fromAddress=self.evm_address,
                                                                gasLimit=estimated_gas)

        tx_receipt = generate_block_and_get_tx_receipt(sc_node, tx_hash)['result']
        assert_equal('0x1', tx_receipt['status'], 'Transaction failed')
        assert_true(len(tx_receipt['logs'][0]) > 0, 'Receipt does not include logs')
        assert_equal(format_evm(tx_receipt['contractAddress']), smart_contract_address)

        self.__check_storage_value(smart_contract, smart_contract_address, self.evm_address, test_message)

        block = sc_node.rpc_eth_getBlockByNumber('0x3', 'false')
        logging.info(block)

        tx_hash = block['result']['transactions'][0]
        logging.info(sc_node.rpc_eth_getTransactionReceipt(tx_hash))
        logging.info(sc_node.rpc_eth_getTransactionByHash(tx_hash))
        was_exception = False
        test_message = 'This is a message'
        try:
            self.__set_storage_value(smart_contract, smart_contract_address, self.evm_address, test_message,
                                     static_call=True, generate_block=False)
        except EvmExecutionError as err:
            logging.info(err)
            was_exception = True
        finally:
            assert_true(not was_exception, "static call failed but should not have")

        tx_receipt = self.__set_storage_value(smart_contract, smart_contract_address, self.evm_address,
                                              test_message,
                                              static_call=False, generate_block=True)
        assert_equal('0x1', tx_receipt['status'], 'Transaction failed')
        assert_true(len(tx_receipt['logs'][0]) > 0, 'Receipt does not include logs')

        self.__check_storage_value(smart_contract, smart_contract_address, self.evm_address, test_message)

        test_message = 'This is a different message'
        res = self.__set_storage_value(smart_contract, smart_contract_address, self.evm_address, test_message,
                                       static_call=True, generate_block=False)
        assert_true(len(res) == 0)

        tx_receipt = self.__set_storage_value(smart_contract, smart_contract_address, self.evm_address,
                                              test_message,
                                              static_call=False, generate_block=True)

        assert_equal('0x1', tx_receipt['status'], 'Transaction failed')
        assert_true(len(tx_receipt['logs'][0]) > 0, 'Receipt does not include logs')
        self.__check_storage_value(smart_contract, smart_contract_address, self.evm_address, test_message)


if __name__ == "__main__":
    SCEvmStorageContract().main()
