#!/usr/bin/env python3
import logging


from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import CallMethod
from SidechainTestFramework.scutil import generate_next_block, disconnect_sc_nodes_bi, connect_sc_nodes, sync_sc_blocks, generate_next_blocks
from test_framework.util import assert_equal, assert_true, assert_false
from SidechainTestFramework.account_websocket_client import AccountWebsocketClient
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
    - Connect a WS client on SC node 2
    - Initialize the SC nodes
    - Disconnect SC node 1 and SC node 2
    - Generate 600 blocks on SC node 1
    - Reconnect the SC node 1 and SC node 2 and generate a block
    - Verify that we received the websocket event SyncStart
    - Verify that we received the websocket event SyncUpdate (sent every 500 synced block)
    - Verify that we received the websocket event SyncStop
"""

websocket_server_port = 8027


class SCWsAccountServerSyncTest(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=20, number_of_sidechain_nodes=2, websocket_server_port=[None, websocket_server_port])

    def checkSyncUpdate(self, wsEvent, currentBlock, startingBlock):
        assert_true(wsEvent["syncing"])
        assert_equal(wsEvent["status"]["currentBlock"], currentBlock)
        assert_equal(wsEvent["status"]["startingBlock"], startingBlock)
        assert_true("highestBlock" in wsEvent["status"]) #Due to the behavior of the STF we can't check this value


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

        sc_node = self.sc_nodes[0]
        self.sc_ac_setup()

        # Disconnect SC node 1 and SC node 2
        logging.info("Disconnect SC node 1 and SC node 2")
        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)

        # forge 600 blocks on SC node 1
        NUM_BLOCKS = 600
        logging.info("SC node 1 generates {} blocks...".format(NUM_BLOCKS))
        generate_next_blocks(sc_node, "first node", NUM_BLOCKS, verbose=False)

        # Reconnect the nodes
        logging.info("Reconnect the nodes")

        connect_sc_nodes(self.sc_nodes[0], 1)
        generate_next_block(sc_node, "first node")
        sync_sc_blocks(self.sc_nodes)

        logging.info("Block synced on SC node 2")

        # Verify that we receive the SyncStart event after the reconnection
        response = json.loads(ws_connection.recv())
        pprint.pprint(response)
        self.checkSyncUpdate(response["result"], 3, 3)
        
        # Verify that we receive the SyncUpdate event after 500 blocks
        response = json.loads(ws_connection.recv())
        pprint.pprint(response)
        self.checkSyncUpdate(response["result"], 503, 3)

        time.sleep(30)

        # Verify that we receive the SyncStop event after sync all blocks
        response = json.loads(ws_connection.recv())
        pprint.pprint(response)
        assert_false(response["result"]["syncing"])
        
        ws_connection.close()


if __name__ == "__main__":
    SCWsAccountServerSyncTest().main()
