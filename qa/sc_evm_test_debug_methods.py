#!/usr/bin/env python3
import json
import logging

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
        - Deploy a smart contract without initial data
        - Transfer tokens and trace transaction
        - EOA2EOA transfer and trace transaction
        - Trace block with transaction using the debug_traceTransaction and debug_traceBlockByNumber/debug_traceBlockByHash methods
        - Call traceCall method on current block
        - Generate a new block without transaction and call tracer methods
        - Call traceCall method on pending block
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

        # 4byte tracer - native tracer
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

        # ------------------------------------------------------------------------
        # debug_traceCall
        # transaction common to all debug_traceCall requests
        trace_call_transaction = {
            "from": self.evm_address,
            "gas": "0x989680",
            "gasPrice": "0x35a4e900",
            "value": "0x0",
            "nonce": "0x1",
            "input": "0x60806040523480156200001157600080fd5b5060405162000943380380620009438339818101604052810190620000379190620001e8565b62000048816200004f60201b60201c565b5062000403565b806000908051906020019062000067929190620000c6565b508060405162000078919062000264565b60405180910390203373ffffffffffffffffffffffffffffffffffffffff167f3e30f08547b8c2a966cd6fdfee44e50d31cf1d898c22a2e22cc68e455f97b01160405160405180910390a350565b828054620000d49062000328565b90600052602060002090601f016020900481019282620000f8576000855562000144565b82601f106200011357805160ff191683800117855562000144565b8280016001018555821562000144579182015b828111156200014357825182559160200191906001019062000126565b5b50905062000153919062000157565b5090565b5b808211156200017257600081600090555060010162000158565b5090565b60006200018d6200018784620002a6565b6200027d565b905082815260208101848484011115620001a657600080fd5b620001b3848285620002f2565b509392505050565b600082601f830112620001cd57600080fd5b8151620001df84826020860162000176565b91505092915050565b600060208284031215620001fb57600080fd5b600082015167ffffffffffffffff8111156200021657600080fd5b6200022484828501620001bb565b91505092915050565b60006200023a82620002dc565b620002468185620002e7565b935062000258818560208601620002f2565b80840191505092915050565b60006200027282846200022d565b915081905092915050565b6000620002896200029c565b90506200029782826200035e565b919050565b6000604051905090565b600067ffffffffffffffff821115620002c457620002c3620003c3565b5b620002cf82620003f2565b9050602081019050919050565b600081519050919050565b600081905092915050565b60005b8381101562000312578082015181840152602081019050620002f5565b8381111562000322576000848401525b50505050565b600060028204905060018216806200034157607f821691505b6020821081141562000358576200035762000394565b5b50919050565b6200036982620003f2565b810181811067ffffffffffffffff821117156200038b576200038a620003c3565b5b80604052505050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052602260045260246000fd5b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b6000601f19601f8301169050919050565b61053080620004136000396000f3fe608060405234801561001057600080fd5b50600436106100365760003560e01c80634ed3885e1461003b5780636d4ce63c14610057575b600080fd5b61005560048036038101906100509190610285565b610075565b005b61005f6100e8565b60405161006c9190610347565b60405180910390f35b806000908051906020019061008b92919061017a565b508060405161009a9190610330565b60405180910390203373ffffffffffffffffffffffffffffffffffffffff167f3e30f08547b8c2a966cd6fdfee44e50d31cf1d898c22a2e22cc68e455f97b01160405160405180910390a350565b6060600080546100f790610428565b80601f016020809104026020016040519081016040528092919081815260200182805461012390610428565b80156101705780601f1061014557610100808354040283529160200191610170565b820191906000526020600020905b81548152906001019060200180831161015357829003601f168201915b5050505050905090565b82805461018690610428565b90600052602060002090601f0160209004810192826101a857600085556101ef565b82601f106101c157805160ff19168380011785556101ef565b828001600101855582156101ef579182015b828111156101ee5782518255916020019190600101906101d3565b5b5090506101fc9190610200565b5090565b5b80821115610219576000816000905550600101610201565b5090565b600061023061022b8461038e565b610369565b90508281526020810184848401111561024857600080fd5b6102538482856103e6565b509392505050565b600082601f83011261026c57600080fd5b813561027c84826020860161021d565b91505092915050565b60006020828403121561029757600080fd5b600082013567ffffffffffffffff8111156102b157600080fd5b6102bd8482850161025b565b91505092915050565b60006102d1826103bf565b6102db81856103ca565b93506102eb8185602086016103f5565b6102f4816104e9565b840191505092915050565b600061030a826103bf565b61031481856103db565b93506103248185602086016103f5565b80840191505092915050565b600061033c82846102ff565b915081905092915050565b6000602082019050818103600083015261036181846102c6565b905092915050565b6000610373610384565b905061037f828261045a565b919050565b6000604051905090565b600067ffffffffffffffff8211156103a9576103a86104ba565b5b6103b2826104e9565b9050602081019050919050565b600081519050919050565b600082825260208201905092915050565b600081905092915050565b82818337600083830152505050565b60005b838110156104135780820151818401526020810190506103f8565b83811115610422576000848401525b50505050565b6000600282049050600182168061044057607f821691505b602082108114156104545761045361048b565b5b50919050565b610463826104e9565b810181811067ffffffffffffffff82111715610482576104816104ba565b5b80604052505050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052602260045260246000fd5b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b6000601f19601f830116905091905056fea2646970667358221220d2e34eaf0cd1e04bd530b9b1a53ec9b566921c6ce6c64a6efff3fd341bdbeb2b64736f6c634300080400330000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000f496e697469616c206d6573736167650000000000000000000000000000000000"
        }

        # struct/opcode default tracer with verbosity boolean parameters
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceCall", "id": 40,
                              "params": [trace_call_transaction, block_number,
                                         {
                                             "enableMemory": False,
                                             "disableStack": True,
                                             "disableStorage": True,
                                             "enableReturnData": False
                                         }]})
        res = sc_node.ethv1(request)['result']
        assert_true("error" not in res, "debug_traceCall failed for successful smart contract transaction")
        trace_logs_length = len(res['structLogs'])
        assert_true(trace_logs_length > 130, "unexpected number of trace logs, less than 130")

        # call tracer - native tracer
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceCall", "id": 48, "params": [trace_call_transaction, block_number, {"tracer": "callTracer"}]})
        res = sc_node.ethv1(request)['result']
        assert_true(res['type'] == "CREATE", "callTracer type not CALL")

        # call tracer with tracer config parameters
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceCall", "id": 56,
                              "params": [trace_call_transaction, block_number, {"tracer": "callTracer",
                                                        "tracerConfig": {
                                                            "onlyTopCall": True,
                                                            "withLog": True
                                                        }}]})
        res = sc_node.ethv1(request)['result']
        assert_true(res['type'] == "CREATE", "callTracer type not CALL")

        # 4byte tracer - native tracer
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceCall", "id": 64, "params": [trace_call_transaction, block_number, {"tracer": "4byteTracer"}]})
        res = sc_node.ethv1(request)['result']
        assert_true(res is not None, "4byteTracer response empty")

        # traceCall method call using block hash - call tracer
        block_hash = sc_node.rpc_eth_getBlockByNumber(block_number,False)['result']['hash']
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceCall", "id": 48, "params": [trace_call_transaction, block_hash, {"tracer": "callTracer"}]})
        res = sc_node.ethv1(request)['result']
        assert_true(res['type'] == "CREATE", "callTracer type not CALL")

        # traceCall method call using block tag latest (current block) - call tracer
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceCall", "id": 48, "params": [trace_call_transaction, "latest", {"tracer": "callTracer"}]})
        res = sc_node.ethv1(request)['result']
        assert_true(res['type'] == "CREATE", "callTracer type not CALL")

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

        # ------------------------------------------------------------------------
        # debug_traceCall on pending block
        # struct/opcode default tracer
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceCall", "id": 40,
                              "params": [trace_call_transaction, "pending"]})
        res = sc_node.ethv1(request)['result']
        assert_true("error" not in res, "debug_traceCall failed for successful smart contract transaction")
        trace_logs_length = len(res['structLogs'])
        assert_true(trace_logs_length > 130, "unexpected number of trace logs, less than 130")

        # call tracer - native tracer
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceCall", "id": 48, "params": [trace_call_transaction, "pending", {"tracer": "callTracer"}]})
        res = sc_node.ethv1(request)['result']
        assert_true(res['type'] == "CREATE", "callTracer type not CALL")

        # 4byte tracer - native tracer
        request = json.dumps({"jsonrpc": "2.0", "method": "debug_traceCall", "id": 64, "params": [trace_call_transaction, "pending", {"tracer": "4byteTracer"}]})
        res = sc_node.ethv1(request)['result']
        assert_true(res is not None, "4byteTracer response empty")

if __name__ == "__main__":
    SCEvmDebugMethods().main()
