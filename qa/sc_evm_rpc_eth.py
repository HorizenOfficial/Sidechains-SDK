#!/usr/bin/env python3
import json
import urllib
import http.client
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.scutil import (
    assert_true, generate_next_block,
)
from httpCalls.transaction.allTransactions import allTransactions
from test_framework.util import assert_false, assert_equal

"""
Tests for eth namespace rpc methods.

Configuration:
    - 1 SC node
    - 1 MC node

Test:
    - test eth_getTransactionByHash
    - test some negative case for json rpc commands

"""

REST_HEADERS = {
    'accept': 'application/json',
    'Content-Type': 'application/json',
    'Authorization': 'Basic dXNlcjpIb3JpemVu'
}

# SOME JSON RPC ERROR CODE
PARSE_ERROR_CODE = 32700
INVALID_REQUEST_CODE = 32600
METHOD_NOT_FOUND_CODE = 32601
INVALID_PARAMS_CODE = 32602


def do_rpc_call(node, payload, headers=None):
    if headers is None:
        headers = REST_HEADERS

    port = str(urllib.parse.urlparse(node.url).port)
    conn = http.client.HTTPConnection("127.0.0.1:" + port)
    conn.request("POST", "/ethv1", payload, headers)
    res = conn.getresponse()
    code = res.getcode()
    data = res.read()
    return code, json.loads(data.decode("utf-8"))


def check_result(code, result, expected_http_code, expected_string_in_result, expected_rpc_code=0):
    print("RPC result = {}".format(result))
    assert_equal(expected_http_code, code)
    assert_true('error' in result)
    assert_true((result['error']['code'] == -expected_rpc_code))
    assert_true('message' in result['error'])
    assert_true(expected_string_in_result in result['error']['message'])


class SCEvmRPCEth(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=20)

    def run_test(self):
        sc_node = self.sc_nodes[0]

        ft_amount_in_zen = Decimal('3000.0')
        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        evm_address_sc1 = self.evm_address[2:]
        evm_address_sc2 = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        #######################################################################################
        # eth_getTransactionByHash tests
        #######################################################################################

        # Test with invalid input transaction id

        res = sc_node.rpc_eth_getTransactionByHash("0xcccbbb")
        assert_true("error" in res)
        assert_false("result" in res)
        assert_true("Invalid params" in res['error']['message'])

        # Test with valid input transaction id but not existing tx
        res = sc_node.rpc_eth_getTransactionByHash("0xcccbbbaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        assert_false("error" in res)
        assert_true("result" in res)
        assert_true(res["result"] is None)

        # Test with a transaction still in the mempool

        tx_id = "0x" + createEIP1559Transaction(sc_node,
                                                fromAddress=evm_address_sc1,
                                                toAddress=evm_address_sc2,
                                                value=1,
                                                nonce=0
                                                )

        res = sc_node.rpc_eth_getTransactionByHash(tx_id)
        assert_false("error" in res)
        assert_true("result" in res)

        assert_equal(tx_id, res["result"]['hash'])
        assert_true(res["result"]['blockHash'] is None)
        assert_true(res["result"]['blockNumber'] is None)
        assert_true(res["result"]['transactionIndex'] is None)

        # Test with a transaction in the blockchain

        block_id = generate_next_block(sc_node, "first node")

        # Verify that the mempool is empty
        response = allTransactions(sc_node, False)
        assert_equal(0, len(response['transactionIds']))

        res = sc_node.rpc_eth_getTransactionByHash(tx_id)
        assert_false("error" in res)
        assert_true("result" in res)

        assert_equal(tx_id, res["result"]['hash'])

        res_block = sc_node.block_findById(blockId=block_id)

        assert_equal("0x" + block_id, res["result"]['blockHash'])
        assert_equal(res_block['result']['height'], int(res["result"]['blockNumber'][2:], 16))
        assert_equal("0x0", res["result"]['transactionIndex'])

        # --------------------------------------------------

        # missing request id
        payload = json.dumps({
            "jsonrpc": "2.0",
            "method": "eth_chainId",
            "params": []
        })
        code, result = do_rpc_call(sc_node, payload)
        check_result(code, result, expected_http_code=400, expected_string_in_result='missing field: id',
                     expected_rpc_code=INVALID_REQUEST_CODE)

        # missing params string (allowed)
        payload = json.dumps({
            "jsonrpc": "2.0",
            "method": "eth_chainId",
            "id": 1
        })
        code, result = do_rpc_call(sc_node, payload)
        assert_equal(200, code)
        expected_result = {'jsonrpc': '2.0', 'id': 1, 'result': '0x3b9aca01'}
        assert_equal(expected_result, result)

        # wrong params string (this command has no parameters)
        payload = json.dumps({
            "jsonrpc": "2.0",
            "method": "eth_chainId",
            "id": 1,
            "params": [{"qqq": 1}]
        })
        code, result = do_rpc_call(sc_node, payload)
        check_result(code, result, expected_http_code=200, expected_string_in_result='Invalid params',
                     expected_rpc_code=INVALID_PARAMS_CODE)

        # missing jsonrpc string
        payload = json.dumps({
            "method": "eth_chainId",
            "id": 1,
            "params": []
        })
        code, result = do_rpc_call(sc_node, payload)
        check_result(code, result, expected_http_code=400, expected_string_in_result='missing field: jsonrpc',
                     expected_rpc_code=INVALID_REQUEST_CODE)

        # wrong jsonrpc string version
        payload = json.dumps({
            "method": "eth_chainId",
            "id": 1,
            "params": [],
            "jsonrpc": "1.0",
        })
        code, result = do_rpc_call(sc_node, payload)
        check_result(code, result, expected_http_code=400, expected_string_in_result='jsonrpc value is not valid',
                     expected_rpc_code=INVALID_REQUEST_CODE)

        # wrong method specified (HTTP CODE 200)
        payload = json.dumps({
            "method": "eth_chainId_",
            "id": 1,
            "params": [],
            "jsonrpc": "2.0",
        })
        code, result = do_rpc_call(sc_node, payload)
        check_result(code, result, expected_http_code=200, expected_string_in_result='Method',
                     expected_rpc_code=METHOD_NOT_FOUND_CODE)

        # batch request with a failure, the expected output is an Array containing the corresponding Response objects
        payload = json.dumps([
            {
                "jsonrpc": "2.0",
                "method": "net_version",
                "params": [],
                "id": 1
            },
            {
                "jsonrpc": "2.0",
                "method": "eth_chainId",
                "params": []
            }
        ])
        code, result = do_rpc_call(sc_node, payload)
        assert_equal(400, code)
        expected_result = [
            {'jsonrpc': '2.0', 'id': 1, 'result': '1000000001'},
            {'error': {'code': -32600, 'message': 'Invalid request: missing field: id', 'data': 'missing field: id'}, 'jsonrpc': '2.0', 'id': None}
        ]
        assert_equal(expected_result, result)

        # empty batch request
        payload = json.dumps([])
        code, result = do_rpc_call(sc_node, payload)
        check_result(code, result, expected_http_code=400, expected_string_in_result='Invalid request',
                     expected_rpc_code=INVALID_REQUEST_CODE)

        # batch request with just a failure, the expected output is a an array with a single error json
        payload = json.dumps([1])
        code, result = do_rpc_call(sc_node, payload)
        assert_equal(400, code)
        expected_result = [
            {'error': {'code': -32600, 'message': 'Invalid request: missing field: id', 'data': 'missing field: id'}, 'jsonrpc': '2.0', 'id': None}
        ]
        assert_equal(expected_result, result)

        # batch with a single request, the response should be a json array with a single result (not just a result)
        payload = json.dumps([
            {
                "jsonrpc": "2.0",
                "method": "net_version",
                "params": [],
                "id": 1
            }])

        code, result = do_rpc_call(sc_node, payload)
        assert_equal(200, code)
        expected_result = [{'jsonrpc': '2.0', 'id': 1, 'result': '1000000001'}]
        assert_equal(expected_result, result)

        # batch with an invalid json format
        payload = '[{"jsonrpc": "2.0", "method": "net_version", "params": [], "id": 1}, {"jsonrpc": "2.0", "method"]'

        code, result = do_rpc_call(sc_node, payload)
        check_result(code, result, expected_http_code=400, expected_string_in_result='Parse error',
                     expected_rpc_code=PARSE_ERROR_CODE)
        expected_result = "{'error': {'code': -32700, 'message': \"Parse error"
        assert_true(str(result).startswith(expected_result))


if __name__ == "__main__":
    SCEvmRPCEth().main()
