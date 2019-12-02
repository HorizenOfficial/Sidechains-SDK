#!/usr/bin/env python2
import json

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, Account
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, is_mainchain_block_included, check_sidechain_boxes

"""
Check the bootstrap feature.

Configuration: bootstrap 3 SC Nodes and start them with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 3 SC nodes
    - Extract genesis info
    - Start SC nodes with that genesis info

Test:
    -for each SC node verify:
        - all keys/boxes/balances are coherent with the default initialization
        - verify MC block is included inside all 3 SC nodes
"""
class SCBootstrap(SidechainTestFramework):

    number_of_sidechain_nodes = 3
    sc_nodes_bootstrap_info=None

    def setup_nodes(self):
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        sc_node_3_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, "1".zfill(64), 100, 1000),
                                         sc_node_1_configuration, sc_node_2_configuration, sc_node_3_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options.tmpdir, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        mc_nodes = self.nodes
        print "Number of MC nodes: {0}".format(len(mc_nodes))
        assert_equal(1, len(mc_nodes), "The number of MC nodes is grater than 1.")

        # Check validity of genesis information
        for i in range(self.number_of_sidechain_nodes):
            node = self.sc_nodes[i]
            mc_block = mc_nodes[0].getblock(str(self.sc_nodes_bootstrap_info.mainchain_block_height))
            # verify MC block reference's inclusion
            res = is_mainchain_block_included(node, self.sc_nodes_bootstrap_info.sidechain_id, 1, 0, mc_block)
            assert_true(res, "The mainchain block is not included for SC node {0}.".format(i))
            # check all keys/boxes/balances are coherent with the default initialization
            check_sidechain_boxes(node, self.sc_nodes_bootstrap_info.sidechain_id,
                                  [self.sc_nodes_bootstrap_info.genesis_account.publicKey], 1,
                                  [self.sc_nodes_bootstrap_info.genesis_account_balance])


if __name__ == "__main__":
    SCBootstrap().main()
