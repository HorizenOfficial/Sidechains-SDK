#!/usr/bin/env python3
import json
import logging
import time

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import deploy_smart_contract, contract_function_call, \
    generate_block_and_get_tx_receipt
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
        assert_equal(1, tx_status, "Error in tx - unrelated to debug methods")

        # -------------------------------------------------------------------------------------
        # debug_traceTransaction method test
        # struct/opcode default tracer
        # do a  check on the number of trace logs (more than 130)
        res = sc_node.rpc_debug_traceTransaction(tx_hash)['result']
        assert_true("error" not in res, "debug_traceTransaction failed for successful smart contract transaction")
        trace_logs_length = len(res['structLogs'])
        assert_true(trace_logs_length > 130, "unexpected number of trace logs, less than 130")

        # struct/opcode default tracer with verbosity boolean parameters
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceTransaction","id": 6,
            "params": [tx_hash,
                {
                    "enableMemory": False,
                    "disableStack": True,
                    "disableStorage": True,
                    "enableReturnData": False
                }]})
        res = sc_node.ethv1(request)['result']
        assert_true("error" not in res, "debug_traceTransaction failed for successful smart contract transaction")
        trace_logs_length = len(res['structLogs'])
        assert_true(trace_logs_length > 130, "unexpected number of trace logs, less than 130")

        # call tracer - native tracer
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceTransaction", "id": 12, "params": [tx_hash, {"tracer": "callTracer"}]})
        res = sc_node.ethv1(request)['result']
        assert_true(res['type'] == "CALL", "callTracer type not CALL")

        # call tracer with tracer config parameters
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceTransaction", "id": 18,
            "params": [tx_hash, {"tracer": "callTracer",
                    "tracerConfig": {
                        "onlyTopCall": True,
                        "withLog": True
                    }}]})
        res = sc_node.ethv1(request)['result']
        assert_true(res['type'] == "CALL", "callTracer type not CALL")

        # 4byte tracer - native tracer
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceTransaction","id": 24, "params": [tx_hash, {"tracer": "4byteTracer"}]})
        res = sc_node.ethv1(request)['result']
        assert_true(res is not None, "4byteTracer response empty")

        # -------------------------------------------------------------------------------------
        # debug_traceBlockByNumber and debug_traceBlockByHash rpc methods
        # struct/opcode default tracer
        block_number = sc_node.rpc_eth_blockNumber()["result"]
        res = sc_node.rpc_debug_traceBlockByNumber(block_number)["result"]
        assert_true(len(res) == 1, "debug results have more than one element")
        res_item = res[0]
        assert_true("error" not in res_item, "debug_traceTransaction failed for successful smart contract transaction")
        trace_logs_length = len(res_item['structLogs'])
        assert_true(trace_logs_length > 130, "unexpected number of trace logs, less than 130")

        # struct/opcode default tracer with verbosity boolean parameters
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceBlockByNumber","id": 8,
                              "params": [block_number,
                                         {
                                             "enableMemory": False,
                                             "disableStack": True,
                                             "disableStorage": True,
                                             "enableReturnData": False
                                         }]})
        res = sc_node.ethv1(request)['result']
        assert_true("error" not in res, "debug_traceBlockByNumber failed for successful smart contract transaction")
        assert_true(len(res) == 1, "debug results have more than one element")
        res_item = res[0]
        assert_true("error" not in res_item, "debug_traceTransaction failed for successful smart contract transaction")
        trace_logs_length = len(res_item['structLogs'])
        assert_true(trace_logs_length > 130, "unexpected number of trace logs, less than 130")

        # call tracer - native tracer
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceBlockByNumber", "id": 16, "params": [block_number, {"tracer": "callTracer"}]})
        res = sc_node.ethv1(request)['result']
        assert_true(len(res) == 1, "debug results have more than one element")
        res_item = res[0]
        assert_true(res_item['type'] == "CALL", "callTracer type not CALL")

        # call tracer with tracer config parameters
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceBlockByNumber", "id": 24,
                              "params": [block_number, {"tracer": "callTracer",
                                                   "tracerConfig": {
                                                       "onlyTopCall": True,
                                                       "withLog": True
                                                   }}]})
        res = sc_node.ethv1(request)['result']
        assert_true(len(res) == 1, "debug results have more than one element")
        res_item = res[0]
        assert_true(res_item['type'] == "CALL", "callTracer type not CALL")

        # 4byte tracer - natve tracer
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceBlockByNumber","id": 32, "params": [block_number, {"tracer": "4byteTracer"}]})
        res = sc_node.ethv1(request)['result']
        assert_true(len(res) == 1, "debug results have more than one element")
        res_item = res[0]
        assert_true(res_item is not None, "4byteTracer response empty")

        # trace block by number with struct default logger
        # the rpc_debug_traceBlockByHash use the same implementation of the rpc_debug_traceBlockByNumber method
        block_hash = sc_node.rpc_eth_getBlockByNumber(block_number,False)['result']['hash']
        res = sc_node.rpc_debug_traceBlockByHash(block_hash)
        assert_true("error" not in res["result"], 'debug_traceBlockByHash failed')

        # -------------------------------------------------------------------------------------
        # generate a new block without transactions and call the debug methods to check if everything works
        tx_status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_equal(1, tx_status, "Error in tx - unrelated to debug methods")

        block_number = sc_node.rpc_eth_blockNumber()["result"]
        res = sc_node.rpc_debug_traceBlockByNumber(block_number)["result"]
        assert_true(len(res) == 0, "debug results have more than zero element")

        block_hash = sc_node.rpc_eth_getBlockByNumber(block_number,False)['result']['hash']
        res = sc_node.rpc_debug_traceBlockByHash(block_hash)["result"]
        assert_true(len(res) == 0, "debug results have more than zero element")


if __name__ == "__main__":
    SCEvmDebugMethods().main()
