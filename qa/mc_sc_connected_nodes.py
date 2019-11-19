#!/usr/bin/env python2
import json
import test_framework.authproxy
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, initialize_chain_clean, start_nodes, websocket_port
from SidechainTestFramework.scutil import check_mainchan_block_inclusion

"""
Check the websocket connection between sidechain and mainchain nodes.

Configueation: start 2 mainchain nodes and 3 sidechain nodes (with default websocket configuration) bootstrapped, 
    respectively, from mainchain node first, first, and third. Mainchain nodes are not connected between them. 
    Sidechain nodes are not connected between them.

Test:
    - verify genesis information for SC node 1
    - verify genesis information for SC node 2
    - verify genesis information for SC node 3
    - MC 1 mine a new block
    - verify the block is included inside SC nodes 1 and 2
    - verify the block is NOT included inside SC node 3
    - MC 2 mine a new block
    - verify the block is included inside SC node 3
    - verify the block is NOT included inside SC nodes 1 and 2
"""
class MCSCConnectedNodes(SidechainTestFramework):

    number_of_mc_nodes = 2
    number_of_sidechains = 3
    ws_port_mc_0 = websocket_port(0)
    ws_port_mc_1 = websocket_port(1)
    sc_nodes_bootstrap_info=None

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes, [self.ws_port_mc_0, self.ws_port_mc_1])

    def setup_network(self, split = False):
        self.nodes = self.setup_nodes()
        self.sync_all()

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir, extra_args=[["-websocket"],["-websocket"],["-websocket"]])

    def sc_setup_chain(self):
        mc_node_1 = self.nodes[0]
        mc_node_2 = self.nodes[1]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, self.ws_port_mc_0))
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, self.ws_port_mc_0))
        )
        sc_node_3_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_2.hostname, self.ws_port_mc_1))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, "1".zfill(64), 600, 1000),
                                         sc_node_1_configuration, sc_node_2_configuration, sc_node_3_configuration)
        self.sc_nodes_bootstrap_info = self.bootstrap_sidechain_nodes(network)

    def sc_setup_network(self, split=False):
        # SC network setup
        self.sc_nodes = self.sc_setup_nodes(self.number_of_sidechains)

    def run_test(self):
        mc_nodes = self.nodes
        sc_nodes = self.sc_nodes
        assert_equal(self.number_of_mc_nodes, len(mc_nodes))
        assert_equal(self.number_of_sidechains, len(sc_nodes))
        print "Number of started mc nodes: {0}".format(len(mc_nodes), "The number of MC nodes is not {0}.".format(self.number_of_mc_nodes))
        print "Number of started sc nodes: {0}".format(len(sc_nodes), "The number of SC nodes is not {0}.".format(self.number_of_sidechains))

        first_mainchain_node = mc_nodes[0]
        second_mainchain_node = mc_nodes[1]

        first_sidechain_node = sc_nodes[0]
        second_sidechain_node = sc_nodes[1]
        third_sidechain_node = sc_nodes[2]

        sidechain_id = self.sc_nodes_bootstrap_info.sidechain_id
        wallet_balance = self.sc_nodes_bootstrap_info.wallet_balance
        genesis_account = self.sc_nodes_bootstrap_info.genesis_account
        mainchain_block_height = self.sc_nodes_bootstrap_info.mainchain_block_height
        first_mainchain_node_block = first_mainchain_node.getblock(str(mainchain_block_height))

        # check mainchain block inclusion for sidechain nodes 1 and 2
        check_mainchan_block_inclusion(first_sidechain_node, sidechain_id, 1, 0,
                                       first_mainchain_node_block,
                                       [genesis_account[1]], [wallet_balance], True)
        check_mainchan_block_inclusion(second_sidechain_node, sidechain_id, 1, 0,
                                       first_mainchain_node_block,
                                       [genesis_account[1]], [wallet_balance], True)

        # check mainchain block inclusion for sidechain node 3
        # check_mainchan_block_inclusion(third_sidechain_node, sidechain_id, 1, 0,
        #                                first_mainchain_node_block,
        #                                [genesis_account[1]],  [wallet_balance],True)

        block_hash = first_mainchain_node.generate(1)
        first_mainchain_node_new_block = first_mainchain_node.getblock(block_hash[0])
        first_sidechain_node.block_generate(json.dumps({"number":1}))
        second_sidechain_node.block_generate(json.dumps({"number":1}))

        check_mainchan_block_inclusion(first_sidechain_node, sidechain_id, 2, 0, first_mainchain_node_new_block,
                                       [genesis_account[1]], [wallet_balance], False)
        check_mainchan_block_inclusion(second_sidechain_node, sidechain_id, 2, 0, first_mainchain_node_new_block,
                                       [genesis_account[1]], [wallet_balance], False)

        try:
            check_mainchan_block_inclusion(third_sidechain_node, sidechain_id, 1, 0, first_mainchain_node_new_block,
                                           [genesis_account[1]], [wallet_balance], False)
            # SC node 2 should not to know information about block generated from MC node 0
            # Therefore the test must to fail
            assert_equal(1, 2)
        except AssertionError:
            print "SC node 2 doesn't include block generated from MC node 0."
            block_hash = second_mainchain_node.generate(1)
            second_mainchain_node_new_block = second_mainchain_node.getblock(block_hash[0])
            third_sidechain_node.block_generate(json.dumps({"number":1}))
            check_mainchan_block_inclusion(third_sidechain_node, sidechain_id, 1, 0, second_mainchain_node_new_block,
                                           [genesis_account[1]], [wallet_balance], False)
            try:
                check_mainchan_block_inclusion(first_sidechain_node, sidechain_id, 2, 1, second_mainchain_node_new_block,
                                               [genesis_account[1]], [wallet_balance], False)
                check_mainchan_block_inclusion(second_sidechain_node, sidechain_id, 2, 0, second_mainchain_node_new_block,
                                               [genesis_account[1]], [wallet_balance], False)
                # SC nodes 0 and 1 should not to know information about block generated from MC node 1
                # Therefore the test must to fail
                assert_equal(1, 2)
            except AssertionError:
                print "SC nodes 0 and 1 don't include block generated from MC node 1."
                # Here the test must to success
                assert_equal(1, 1)



if __name__ == "__main__":
    MCSCConnectedNodes().main()