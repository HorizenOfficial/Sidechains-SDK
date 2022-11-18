#!/usr/bin/env python3
import logging

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import deploy_smart_contract, contract_function_call, \
    CallMethod, eoa_transaction, generate_block_and_get_tx_receipt
from test_framework.util import assert_equal, assert_true

"""
Check debug methods.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the smart contract:
        - Deploy a smart contract without initial data
        - Transfer tokens and trace transaction
        - EOA2EOA transfer and trace transaction
        - Trace block with transaction
"""


class SCEvmDebugMethods(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=100)

    def run_test(self):
        self.sc_ac_setup()
        sc_node = self.sc_nodes[0]

        smart_contract_type = 'TestERC20'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        smart_contract = SmartContract(smart_contract_type)
        logging.info(smart_contract)

        smart_contract_address = deploy_smart_contract(sc_node, smart_contract, self.evm_address)
        ret = sc_node.wallet_createPrivateKeySecp256k1()
        other_address = ret["result"]["proposition"]["address"]
        transfer_amount = 99

        method = 'transfer(address,uint256)'
        tx_hash = contract_function_call(sc_node, smart_contract, smart_contract_address, self.evm_address, method,
                                         other_address, transfer_amount)

        tx_status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_equal(tx_status, 1, "Error in tx - unrelated to debug methods")

        res = sc_node.rpc_debug_traceTransaction(tx_hash)['result']
        assert_true("error" not in res, "debug_traceTransaction failed for successful smart contract transaction")

        tx_hash = eoa_transaction(sc_node, from_addr=self.evm_address, to_addr=other_address,
                                  call_method=CallMethod.RPC_EIP155, value=transfer_amount)

        tx_status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_equal(tx_status, 1, "Error in tx - unrelated to debug methods")

        res = sc_node.rpc_debug_traceTransaction(tx_hash)
        assert_true("error" not in res['result'], "debug_traceTransaction failed for successful eoa transfer")

        res = sc_node.rpc_debug_traceBlockByNumber("0x4")
        assert_true("error" not in res["result"], 'debug_traceBlockByNumber failed')


if __name__ == "__main__":
    SCEvmDebugMethods().main()
