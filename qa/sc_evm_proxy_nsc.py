#!/usr/bin/env python3
import logging
import pprint

from eth_utils import add_0x_prefix, function_signature_to_4byte_selector, encode_hex, remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import deploy_smart_contract, ac_invokeProxy
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.utils import FORGER_STAKE_SMART_CONTRACT_ADDRESS, PROXY_SMART_CONTRACT_ADDRESS
from SidechainTestFramework.scutil import generate_next_blocks, generate_next_block
from httpCalls.transaction.allTransactions import allTransactions
from test_framework.util import assert_equal, assert_false, assert_true

"""
Check an EVM Contract calling a native contract.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    - Test using a Proxy native smart contract for invoking a solidity smart contract
"""


class SCEvmProxyNsc(AccountChainSetup):

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
        sc_node = self.sc_nodes[0]

        # Deploy Smart Contract
        smart_contract_type = 'StorageTestContract'
        logging.info(f"Creating smart contract utilities for {smart_contract_type}")
        smart_contract = SmartContract(smart_contract_type)
        logging.info(smart_contract)
        initial_message = 'Initial message'
        tx_hash, smart_contract_address = smart_contract.deploy(sc_node, initial_message,
                                                                fromAddress=self.evm_address,
                                                                gasLimit=10000000,
                                                                gasPrice=900000000)

        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()

        # check we succesfully deployed the smart contract and we can get the initial string
        res = smart_contract.static_call(sc_node, 'get()', fromAddress=self.evm_address, toAddress=smart_contract_address, gasPrice=900000000)
        assert_equal(initial_message, res[0])

        # change the contract storage via proxy native smart contract
        method = 'set(string)'
        new_message = 'Room to roam'

        data = smart_contract.raw_encode_call(method, new_message)

        # invoke the proxy native smart contract
        tx_hash = ac_invokeProxy(sc_node, remove_0x_prefix(smart_contract_address), data, nonce=None)['result']['transactionId']
        self.sc_sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        txJsonResult = sc_node.rpc_eth_getTransactionByHash(add_0x_prefix(tx_hash))['result']
        pprint.pprint(txJsonResult)

        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
        status = int(receipt['result']['status'], 16)
        assert_true(status == 1)
        gas_used = receipt['result']['gasUsed']

        # read the new value in the solidity smart contract and check we succesfully modified it
        res = smart_contract.static_call(sc_node, 'get()', fromAddress=self.evm_address, toAddress=smart_contract_address, gasPrice=900000000)
        assert_equal(new_message, res[0])


if __name__ == "__main__":
    SCEvmProxyNsc().main()
