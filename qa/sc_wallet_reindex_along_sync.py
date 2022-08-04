#!/usr/bin/env python2
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, assert_not_equal
from SidechainTestFramework.scutil import connect_sc_nodes, bootstrap_sidechain_nodes, start_sc_nodes, \
    generate_next_blocks, sync_sc_blocks
from httpCalls.wallet.reindex import http_wallet_reindex, http_wallet_reindex_status, http_debug_reindex_step
"""
Check that a node can answer to block requests from another node even if is synching.
Configuration: start 2 sidechain nodes and 1 Mainchain node, from which the sidechain is bootstrapped.

    Steps:
        - make  node 1 create 2000 blocks
        - put node 1 in resync status
        - make node 2 connectTo the node 1 ( it should start to Synchronize right after)

Test:
    - verify that:
        - node2 can complete sync with node1
"""

def sc_create_multiple_not_connected_nodes_network(mc_node_1, num_of_nodes_to_start, mc_node_index, apikeyNode):
    nodes_config = []
    for i in range(num_of_nodes_to_start):
        a_config = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(mc_node_index))),
            True,
            automatic_fee_computation=False,
            api_key=apikeyNode
        )
        nodes_config.append(a_config)
    nodes_tuple = tuple(nodes_config)
    network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, LARGE_WITHDRAWAL_EPOCH_LENGTH), *nodes_tuple)
    return network



BLOCKS_TO_FORGE = 20


class SidechainWalletReindexSyncTest(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2
    sc_nodes_bootstrap_info = None

    API_KEY_NODE = "Horizen"

    # MC
    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    # SC
    def sc_setup_chain(self):
        mc_node_1 = self.nodes[0]

        # multiple not connected nodes network
        network = sc_create_multiple_not_connected_nodes_network(mc_node_1, self.number_of_sidechain_nodes, 0, self.API_KEY_NODE)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 8)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    # RUN TEST
    def run_test(self):
        mc_nodes = self.nodes
        sc_nodes = self.sc_nodes
        print("Number of started mc nodes: {0}".format(len(mc_nodes), "The number of MC nodes is not {0}.".format(
            self.number_of_mc_nodes)))
        print("Number of started sc nodes: {0}".format(len(sc_nodes), "The number of SC nodes is not {0}.".format(
            self.number_of_sidechain_nodes)))

        sc_node1 = self.sc_nodes[0]
        node1Name = "sc_node1"
        sc_node2 = self.sc_nodes[1]

        blocks_numb = BLOCKS_TO_FORGE
        print("gonna forge " + str(blocks_numb) + " blocks")
        generate_next_blocks(sc_node1, node1Name, blocks_numb)
        best1 = sc_node1.block_best()['result']['block']['id']
        best2_0 = sc_node2.block_best()['result']['block']['id']
        print("best 1 - best 2_0")
        print(best1)
        print(best2_0)

        assert_not_equal(best1, best2_0, "They are same already, quite weird")

        print("simultate start reindex on node1")
        http_debug_reindex_step(sc_node1, self.API_KEY_NODE)
        reindexStatus = http_wallet_reindex_status(sc_node1, self.API_KEY_NODE)
        assert_equal(reindexStatus, 'ongoing')

        print("connecting the two nodes...")
        connect_sc_nodes(sc_node1, 1)
        print("sleep 5 seconds to let it start Sync ...")
        time.sleep(5)


        sync_sc_blocks(self.sc_nodes, 200, True)
        best2_2 = (sc_node2.block_best()['result']['block']['id'])
        print("Final check")
        print("best 1" + best1)
        print("best 1_1:" + sc_node1.block_best()['result']['block']['id'])
        print("best 2_2:" + best2_2)

        assert_equal(best1, best2_2, "best 1 should be like best 2 at the end of synchronization")


if __name__ == "__main__":
    SidechainWalletReindexSyncTest().main()
