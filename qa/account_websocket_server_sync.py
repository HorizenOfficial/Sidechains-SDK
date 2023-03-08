#!/usr/bin/env python3
import logging

from eth_utils import add_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import CallMethod, deploy_smart_contract, \
    contract_function_static_call, contract_function_call, ac_makeForgerStake
from SidechainTestFramework.scutil import generate_next_block, disconnect_sc_nodes_bi, connect_sc_nodes, sync_sc_blocks, generate_next_blocks
from test_framework.util import assert_equal, assert_true, forward_transfer_to_sidechain
from SidechainTestFramework.account.httpCalls.transaction.createLegacyTransaction import createLegacyTransaction
from SidechainTestFramework.account_websocket_client import AccountWebsocketClient
from SidechainTestFramework.account.utils import convertZenToZennies, convertZenToWei
import pprint
import json
import time


global_call_method = CallMethod.RPC_EIP155

"""
Check account websocket:
    -syncing method

Configuration: bootstrap 2 SC nodes and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 2 SC nodes
    - Extract genesis info
    - Start SC nodes with that genesis info
    - The SC node 2 starts the account websocket
    
Test:
"""

websocket_server_port = 8027


class SCWsAccountServerSyncTest(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=20, number_of_sidechain_nodes=2, websocket_server_port=[None, websocket_server_port])

    def checkSyncUpdate(self, wsEvent):
        assert_true(wsEvent["syncing"])


    def run_test(self):
        SC_NODE1_ID = 1

        # Start websocket client
        logging.info("Start websocket client")

        ws = AccountWebsocketClient()
        ws_connection = ws.create_connection(f"ws://localhost:{websocket_server_port}/")

        ############## sync ########################
        logging.info("############## sync ########################")

        # SC node 1 subscribe to sync method
        logging.info("SC node 1 subscribe to sync method")

        response = json.loads(ws.sendMessage(ws_connection, SC_NODE1_ID, ws.SUBSCRIBE_REQUEST, [ws.SYNC_SUBSCRIPTION]))
        sync_subscription = response["result"]

        sc_node = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]
        self.sc_ac_setup()

        # Verify that we receive the SyncStart event
        response = json.loads(ws_connection.recv())
        pprint.pprint(response)

        # Disconnect SC node 1 and SC node 2
        logging.info("Disconnect SC node 1 and SC node 2")
        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)

        # forge 1000 blocks on SC node 1
        NUM_BLOCKS = 600
        logging.info("SC node 1 generates {} blocks...".format(NUM_BLOCKS))
        generate_next_blocks(sc_node, "first node", NUM_BLOCKS, verbose=False)

        # Reconnect the nodes
        logging.info("Reconnect the nodes")

        connect_sc_nodes(self.sc_nodes[0], 1)
        generate_next_block(sc_node, "first node")
        sync_sc_blocks(self.sc_nodes, wait_for=300)


        response = json.loads(ws_connection.recv())
        pprint.pprint(response)


        response = json.loads(ws_connection.recv())
        pprint.pprint(response)

        time.sleep(60)

        response = json.loads(ws_connection.recv())
        pprint.pprint(response)
        
        ws_connection.close()


if __name__ == "__main__":
    SCWsAccountServerSyncTest().main()
