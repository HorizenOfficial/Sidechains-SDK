#!/usr/bin/env python3
import json
import logging
import time
from datetime import datetime

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
    connect_sc_nodes, stop_sc_node, start_sc_node, wait_for_sc_node_initialization, \
    DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND, EVM_APP_BINARY, AccountModel, disconnect_sc_nodes_bi
from test_framework.util import assert_equal, assert_true, fail, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index

"""
Test that the deprecated node gets banned and disconnected immediately during sync, if the block is invalid
Configuration:
    Start 1 MC nodes and 2 SC nodes.
    Node 0 gas limit is 20000, node 1 gas limit is 30000
Test:
    - Start the nodes and generate 1500 blocks on node 0
    - Upon reaching the fork, node 1 should reject block from node 0, set the block as invalid and disconnect node 0
    - manually remove node 0 from the blacklist, restart the node to refresh modifiers cache
    - force sync again and assert that node 0 will be banned
"""

WITHDRAWAL_EPOCH_LENGTH = 10

class EvmSyncStatusDeprecatedNode(SidechainTestFramework):
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
            block_timestamp_rewind=DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND * 5,
            model=AccountModel)

    def sc_setup_nodes(self):
        # start both SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, dirname=self.options.tmpdir,
                              binary = [EVM_APP_BINARY] * 2,
                              extra_args=[['-blockGasLimit', '20000'],['-blockGasLimit', '30000']])

    def run_sc_node(self, sc_node_idx):
        logging.info("Starting SC node " + str(sc_node_idx))
        if sc_node_idx == 0:
            extra_args = ['-blockGasLimit', '20000']
        else:
            extra_args = ['-blockGasLimit', '30000']
        start_sc_node(sc_node_idx,
                      self.options.tmpdir, binary=EVM_APP_BINARY,
                      extra_args=[extra_args])
        wait_for_sc_node_initialization(self.sc_nodes)
        time.sleep(3)

    def restart_sc_node(self, sc_node_idx):
        logging.info("Stopping SC node " + str(sc_node_idx))
        stop_sc_node(self.sc_nodes[sc_node_idx], sc_node_idx)
        time.sleep(2)

        self.run_sc_node(sc_node_idx)

    def run_test(self):
        sc_node0 = self.sc_nodes[0]
        sc_node1 = self.sc_nodes[1]

        # Note: in case machine is too fast we need more blocks to be able to get sync
        num_blocks = 1500

        self.sync_all()

        logging.info("Stopping SC node 1")
        stop_sc_node(sc_node1, 1)

        logging.info("SC node 0 generates {} blocks...".format(num_blocks))
        generate_next_blocks(sc_node0, "node 0", num_blocks, verbose=False)

        # run the sidechain node 2 and sync it
        self.run_sc_node(1)

        logging.info("Connecting SC nodes")
        time.sleep(5)
        connect_sc_nodes(self.sc_nodes[0], 1)
        i = 0
        while i < 30 and len(self.sc_nodes[1].node_blacklistedPeers()["result"]["addresses"]) == 0:
            time.sleep(2)
            i += 1
        assert_true(len(self.sc_nodes[1].node_blacklistedPeers()["result"]["addresses"]) == 1,
                    "Node is not banned for invalid modifier")

        logging.info("Restart node 0 to reset modifiers cache")
        self.restart_sc_node(1)
        time.sleep(10)
        self.sc_nodes[1].node_removeFromBlacklist(json.dumps({"address": "127.0.0.1:8734"}))
        assert_true(len(self.sc_nodes[1].node_blacklistedPeers()["result"]["addresses"]) == 0,
                    "Node is not removed from blacklist")

        connect_sc_nodes(self.sc_nodes[0], 1)
        i = 0
        while i < 30 and len(self.sc_nodes[1].node_blacklistedPeers()["result"]["addresses"]) == 0:
            time.sleep(2)
            i += 1
        assert_true(len(self.sc_nodes[1].node_blacklistedPeers()["result"]["addresses"]) == 1,
                    "Node is not banned for invalid modifier")


if __name__ == "__main__":
    EvmSyncStatusDeprecatedNode().main()