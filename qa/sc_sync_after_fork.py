#!/usr/bin/env python3
import json

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, connect_sc_nodes, is_mainchain_block_included_in_sc_block, \
    check_mainchain_block_reference_info, generate_next_block, generate_next_blocks

"""
Check that after the hard fork, 2 SC nodes are able to communicate

Configuration:
    Start 1 MC node and 2 SC node.
    SC node 1 connected to the MC node 1.
    SC node 2 connected to the SC node 1.

Test:
    For the SC node:
        - Synchronize MC node to the point of SC Creation Block.
        - SC1 forges blocks till it reaches consensus epoch 6
        - Connect SC2 node to the SC1 node so that it gets aligned
        - forge 1 more block in SC2
        - verify it's included in SC1
"""


class SCSyncAfterFork(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2
    sc_nodes_bootstrap_info = None

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        mc_node_1 = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 100, 100),
                                         sc_node_1_configuration, sc_node_2_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 10)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        mc_nodes = self.nodes
        sc_nodes = self.sc_nodes
        print("Number of started mc nodes: {0}".format(len(mc_nodes), "The number of MC nodes is not {0}.".format(
            self.number_of_mc_nodes)))
        print("Number of started sc nodes: {0}".format(len(sc_nodes), "The number of SC nodes is not {0}.".format(
            self.number_of_sidechain_nodes)))

        first_mainchain_node = mc_nodes[0]

        first_sidechain_node = sc_nodes[0]
        second_sidechain_node = sc_nodes[1]

        # SC node 1 block in epoch 0
        generate_next_block(first_sidechain_node, "first node")
        # SC node 1 block in epoch 1
        generate_next_block(first_sidechain_node, "first node", force_switch_to_next_epoch=True)
        # SC node 1 block in epoch 2
        generate_next_block(first_sidechain_node, "first node", force_switch_to_next_epoch=True)
        # SC node 1 block in epoch 3
        generate_next_block(first_sidechain_node, "first node", force_switch_to_next_epoch=True)
        # SC node 1 block in epoch 4
        generate_next_block(first_sidechain_node, "first node", force_switch_to_next_epoch=True)
        # SC node 1 block in epoch 5
        generate_next_block(first_sidechain_node, "first node", force_switch_to_next_epoch=True)
        # SC node 1 block in epoch 6
        generate_next_block(first_sidechain_node, "first node", force_switch_to_next_epoch=True)

        # connect SC 1 to SC 2, generate one more, sync, and verify it's shared
        connect_sc_nodes(self.sc_nodes[0], 1)
        generate_next_block(first_sidechain_node, "first node")
        self.sc_sync_all()

        best_1 = first_sidechain_node.block_best()["result"]
        best_2 = second_sidechain_node.block_best()["result"]

        assert_equal(best_1, best_2, "Nodes are unable to sync, best blocks are not equal")


if __name__ == "__main__":
    SCSyncAfterFork().main()
