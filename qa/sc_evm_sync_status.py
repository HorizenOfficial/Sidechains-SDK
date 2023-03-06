#!/usr/bin/env python3
import logging
import time
from datetime import datetime

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
    connect_sc_nodes, stop_sc_node, start_sc_node, \
    wait_for_sc_node_initialization, DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND, EVM_APP_BINARY, \
    AccountModel, TimeoutException
from test_framework.util import assert_equal, assert_true, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index

"""
Test that the eth_syncing rpc method answer in a correct way if the node is currently syncing a batch of new blocks
from another peer

Configuration:
    Start 1 MC nodes and 2 SC nodes.

Test:
    - synchronize MC node to the point of SC Creation Block
    - start SC1 and SC2 nodes 
    - stop SC2 node
    - forging blocks on node SC1 reaching the end of consensus epoch
    - restart and sync SC2 node checking the eth_syncing rpc method result in case the sync is in progress (syncStatus =/= false)
    - stop SC2 node
    - forging blocks on node SC1 reaching the end of consensus epoch
    - restart and sync SC2 and stop SC1 node after 15 seconds 
    - wait 20 seconds and check that eth_syncing rpc method return syncStatus false
"""

WITHDRAWAL_EPOCH_LENGTH = 10

class EvmSyncStatus(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split=False):
        self.nodes = self.setup_nodes()
        self.sync_all()

    def setup_nodes(self):
        # start MC node
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir,
                           extra_args=[['-debug=sc', '-debug=ws', '-logtimemicros=1', '-scproofqueuesize=0']])

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            False)

        sc_node_2_configuration = SCNodeConfiguration(MCConnectionInfo(), False)

        self.network = SCNetworkConfiguration(SCCreationInfo(mc_node, 600, WITHDRAWAL_EPOCH_LENGTH),
                                              sc_node_1_configuration, sc_node_2_configuration)
        bootstrap_sidechain_nodes(
            self.options,
            self.network,
            block_timestamp_rewind=DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND,
            model=AccountModel)

    def sc_setup_nodes(self):
        # start both SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, dirname=self.options.tmpdir,
                              binary=[EVM_APP_BINARY] * 2)

    def sync_sc_blocks(self, wait_for=360, execute_stop=False):

        # wait for maximum wait_for seconds for everybody to have the same block count
        start = time.time()
        while True:

            # call eth_syncing rpc method, if the sync status result is true check if the block values are correct
            res = self.sc_nodes[1].rpc_eth_syncing()["result"]
            if(isinstance(res, dict) and "currentBlock" in res):
                decimalCurrentBlock = int(res["currentBlock"], 16)
                deciamlStartingBlock = int(res["startingBlock"], 16)
                decimalHighestBlock = int(res["highestBlock"], 16)
                assert_true(decimalCurrentBlock > 0, "unexpected value for currentBlock")
                assert_true(deciamlStartingBlock >= 0, "unexpected value for startingBlock")
                assert_true(decimalHighestBlock > 0, "unexpected value for highestBlock")
                assert_true(decimalHighestBlock - decimalCurrentBlock > 0, "currentBlock is greater than highestBlock")
                assert_true(decimalHighestBlock - deciamlStartingBlock > 0, "startingBlock is greater than highestBlock")

            if time.time() - start >= wait_for:
                raise TimeoutException("Syncing blocks")
            counts = [int(x.block_best()["result"]["height"]) for x in self.sc_nodes]
            if counts == [counts[0]] * len(counts):
                break
            if execute_stop and time.time() - start >= 15:
                stop_sc_node(self.sc_nodes[0],0)
                break
            time.sleep(1)

    def startAndSyncScNode2(self, execute_stop=False):
        logging.info("Starting SC2")
        start_sc_node(1, self.options.tmpdir, binary=EVM_APP_BINARY)
        wait_for_sc_node_initialization(self.sc_nodes)
        time.sleep(2)

        logging.info("Connecting SC2")
        connect_sc_nodes(self.sc_nodes[0], 1)

        logging.info("Syncing...")
        T_0 = datetime.now()
        self.sync_sc_blocks(execute_stop=execute_stop)
        T_1 = datetime.now()
        u_sec = (T_1 - T_0).microseconds
        sec = (T_1 - T_0).seconds
        logging.info("SC2 synced in {}.{} secs".format(sec, u_sec))

    def run_test(self):

        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        self.blocks = []
        self.sync_all()

        # -------------------------------------------------------------------------------------
        # Test 1
        # the test workflow is:
        # stop SC2 node
        # forge 1000 blocks on node SC1
        # restart SC2 and sync the recently created blocks on SC1
        # call the eth_syncing endpoint and if the response is not False check the block height values

        logging.info("Stopping SC2")
        stop_sc_node(sc_node2, 1)

        # forge 1000 blocks on SC1
        NUM_BLOCKS = 1000
        logging.info("SC1 generates {} blocks...".format(NUM_BLOCKS))
        self.blocks.extend(generate_next_blocks(sc_node1, "first node", NUM_BLOCKS, verbose=False))

        # restart the sidechain node 2 and sync it
        time.sleep(2)
        self.startAndSyncScNode2()

        # assert that the block best on SC1 match SC2
        assert_equal(sc_node1.block_best()["result"], sc_node2.block_best()["result"])
        self.sync_all()

        # -------------------------------------------------------------------------------------
        # Test 2
        # the test workflow is:
        # stop SC2 node
        # forge 2500 blocks on node SC1
        # restart SC2 and sync the recently created blocks on SC1 but stop after 15 seconds the SC1 node
        # call the eth_syncing endpoint and if the response is not False check the block height values
        # wait 20 seconds and check if the eth_syncing method return False

        logging.info("Stopping SC2")
        stop_sc_node(sc_node2, 1)

        # forge 1000 blocks on SC1
        NUM_BLOCKS = 2500
        logging.info("SC1 generates {} blocks...".format(NUM_BLOCKS))
        self.blocks.extend(generate_next_blocks(sc_node1, "first node", NUM_BLOCKS, verbose=False))

        # restart the sidechain node 2 and sync it
        time.sleep(2)
        self.startAndSyncScNode2(execute_stop=True)
        time.sleep(60)
        res = self.sc_nodes[1].rpc_eth_syncing()["result"]
        assert_true(res == False, "unexpected value for eth_syncing result")

if __name__ == "__main__":
    EvmSyncStatus().main()