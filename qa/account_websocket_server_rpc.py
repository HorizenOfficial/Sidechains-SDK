#!/usr/bin/env python3
import logging


from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import CallMethod
from SidechainTestFramework.scutil import generate_next_block
from test_framework.util import assert_equal, assert_true
from SidechainTestFramework.account_websocket_client import AccountWebsocketClient
from SidechainTestFramework.account.httpCalls.transaction.createLegacyTransaction import createLegacyTransaction
from SidechainTestFramework.account.utils import convertZenToWei
import pprint
import json


global_call_method = CallMethod.RPC_EIP155

"""
Check account websocket:
    -rpc methods

Configuration: bootstrap 1 SC nodes and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC nodes
    - Extract genesis info
    - Start SC nodes with that genesis info
    - The SC node 1 starts the account websocket
    
Test:
    - Connect a WS client on SC node 1
    - Initialize the SC nodes
    - Verify that we are able to call eth_getBlockByNumber using the websocket interface
    - Forge a new block
    - Verify that we are able to call eth_getBlockByNumber using the websocket interface and we get an updated response
    - Verify that we are able to call eth_chainId using the websocket interface
    - Verify that we are able to call txpool_status using the websocket interface
    - Send a new transaction
    - Verify that we are able to call txpool_status using the websocket interface and we get an updated response
    - Verify that we are able to call net_version using the websocket interface
    - Verify that we are able to call web3_clientVersion using the websocket interface

"""

websocket_server_port = 8028


class SCWsAccountServerRpcTest(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=20, number_of_sidechain_nodes=1, websocket_server_port=[websocket_server_port])

    def checkRPCResponseFields(self, json_response):
        assert_true("id" in json_response)
        assert_true("jsonrpc" in json_response)
        assert_true("result" in json_response)


    def run_test(self):
        SC_NODE1_ID = 1

        # Start websocket client
        logging.info("Start websocket client")

        ws = AccountWebsocketClient()
        ws_connection = ws.create_connection(f"ws://localhost:{websocket_server_port}/")

        sc_node = self.sc_nodes[0]
        self.sc_ac_setup()

        ############## eth_getBlockByNumber ########################
        logging.info("############## eth_getBlockByNumber ########################")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, "eth_getBlockByNumber", ["latest", "true"]))
        self.checkRPCResponseFields(response)
        result = response["result"]
        assert_equal(result["number"], "0x2")

        # Generate 1 block
        generate_next_block(sc_node, "First node")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, "eth_getBlockByNumber", ["latest", "true"]))
        self.checkRPCResponseFields(response)
        result = response["result"]
        assert_equal(result["number"], "0x3")

        ############## eth_chainId ########################
        logging.info("############## eth_chainId ########################")
        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, "eth_chainId", []))
        self.checkRPCResponseFields(response)
        result = response["result"]
        assert_equal(result, "0x3b9aca01")


        ############## txpool_status ########################
        logging.info("############## txpool_status ########################")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, "txpool_status", []))
        self.checkRPCResponseFields(response)
        result = response["result"]
        assert_equal(result["pending"], 0)
        assert_equal(result["queued"], 0)


        sc_node1_address = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        # Create one transaction
        createLegacyTransaction(sc_node,
                                    toAddress=sc_node1_address,
                                    value=convertZenToWei(5),
                                    )
        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, "txpool_status", []))
        self.checkRPCResponseFields(response)
        result = response["result"]
        assert_equal(result["pending"], 1)
        assert_equal(result["queued"], 0)

        ############## net_version ########################
        logging.info("############## net_version ########################")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, "net_version", []))
        self.checkRPCResponseFields(response)


        ############## web3_clientVersion ########################
        logging.info("############## web3_clientVersion ########################")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, "web3_clientVersion", []))
        self.checkRPCResponseFields(response)


        ws_connection.close()


if __name__ == "__main__":
    SCWsAccountServerRpcTest().main()
