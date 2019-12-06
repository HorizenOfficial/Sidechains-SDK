#!/usr/bin/env python2
import json
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, connect_nodes_bi, assert_true, assert_false
from SidechainTestFramework.scutil import check_regularbox_balance, connect_sc_nodes, \
    bootstrap_sidechain_nodes, start_sc_nodes, is_mainchain_block_included_in_sc_block, sc_generate_blocks, \
    is_mainchain_block_included_in_sidechain_block_reference_info

"""
Check the websocket connection between sidechain and mainchain nodes.

Configuration: start 2 mainchain nodes and 3 sidechain nodes (with default websocket configuration) bootstrapped, 
    respectively, from mainchain node first, first, and third. Mainchain nodes are not connected between them. 
    Sidechain nodes are not connected between them.

Test:
    - verify genesis information for SC node 1, 2 and 3
    - MC 1 mine a new block
    - SC node 1 and 2 forge 1 SC block
    - verify the block is included inside SC nodes 1 and 2
    - verify the block is NOT included inside SC node 3
    - connect MC 1 to MC 2
    - connect SC 1 to SC 2 and SC 3
    - verify the block is included inside SC node 3
    - MC 2 mine a new block
    - SC node 3 forges 1 SC block
    - verify the block is included inside SC nodes 1, 2 and 3
"""
class MCSCConnectedNodes(SidechainTestFramework):

    number_of_mc_nodes = 2
    number_of_sidechain_nodes = 3
    sc_nodes_bootstrap_info=None

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        mc_node_1 = self.nodes[0]
        mc_node_2 = self.nodes[1]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
        )
        sc_node_3_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_2.hostname, websocket_port_by_mc_node_index(1)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, "1".zfill(64), 600, 1000),
                                         sc_node_1_configuration, sc_node_2_configuration, sc_node_3_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options.tmpdir, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        mc_nodes = self.nodes
        sc_nodes = self.sc_nodes
        assert_equal(self.number_of_mc_nodes, len(mc_nodes))
        assert_equal(self.number_of_sidechain_nodes, len(sc_nodes))
        print "Number of started mc nodes: {0}".format(len(mc_nodes), "The number of MC nodes is not {0}.".format(self.number_of_mc_nodes))
        print "Number of started sc nodes: {0}".format(len(sc_nodes), "The number of SC nodes is not {0}.".format(self.number_of_sidechain_nodes))

        first_mainchain_node = mc_nodes[0]
        second_mainchain_node = mc_nodes[1]

        first_sidechain_node = sc_nodes[0]
        second_sidechain_node = sc_nodes[1]
        third_sidechain_node = sc_nodes[2]

        wallet_balance = self.sc_nodes_bootstrap_info.genesis_account_balance
        genesis_account = self.sc_nodes_bootstrap_info.genesis_account
        mainchain_block_height = self.sc_nodes_bootstrap_info.mainchain_block_height
        first_mainchain_node_block = first_mainchain_node.getblock(str(mainchain_block_height))

        # verify genesis information for SC node 1, 2 and 3
        # verify the mc block is included inside SC nodes 1, 2 and 3
        first_sc_node_best_block = first_sidechain_node.block_best()["result"]
        second_sc_node_best_block = second_sidechain_node.block_best()["result"]
        third_sc_node_best_block = third_sidechain_node.block_best()["result"]

        assert_equal(first_sc_node_best_block["height"], 1, "The best block has not the specified height.")
        assert_equal(second_sc_node_best_block["height"], 1, "The best block has not the specified height.")
        assert_equal(third_sc_node_best_block["height"], 1, "The best block has not the specified height.")

        sc_1_mc_block_inclusion = is_mainchain_block_included_in_sc_block(first_sc_node_best_block["block"],
                                                              first_mainchain_node_block)
        sc_2_mc_block_inclusion = is_mainchain_block_included_in_sc_block(second_sc_node_best_block["block"],
                                                              first_mainchain_node_block)
        sc_3_mc_block_inclusion = is_mainchain_block_included_in_sc_block(third_sc_node_best_block["block"],
                                                              first_mainchain_node_block)
        assert_true(sc_1_mc_block_inclusion, "The mainchain block is not included for SC node 1.")
        assert_true(sc_2_mc_block_inclusion, "The mainchain block is not included for SC node 2.")
        assert_true(sc_3_mc_block_inclusion, "The mainchain block is not included for SC node 3.")

        first_sc_mc_best_block_ref_info = first_sidechain_node.mainchain_bestBlockReferenceInfo()["result"]
        second_sc_mc_best_block_ref_info = second_sidechain_node.mainchain_bestBlockReferenceInfo()["result"]
        third_sc_mc_best_block_ref_info = third_sidechain_node.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            is_mainchain_block_included_in_sidechain_block_reference_info(
                first_sc_mc_best_block_ref_info, first_mainchain_node_block),
            "The mainchain block is not included inside SC block reference info.")
        assert_true(
            is_mainchain_block_included_in_sidechain_block_reference_info(
                second_sc_mc_best_block_ref_info, first_mainchain_node_block),
            "The mainchain block is not included inside SC block reference info.")
        assert_true(
            is_mainchain_block_included_in_sidechain_block_reference_info(
                third_sc_mc_best_block_ref_info, first_mainchain_node_block),
            "The mainchain block is not included inside SC block reference info.")

        check_regularbox_balance(first_sidechain_node, [genesis_account.publicKey], [1], [wallet_balance])
        check_regularbox_balance(second_sidechain_node, [genesis_account.publicKey], [1], [wallet_balance])
        check_regularbox_balance(third_sidechain_node, [genesis_account.publicKey], [1], [wallet_balance])

        # MC 1 mine a new block
        block_hash = first_mainchain_node.generate(1)[0]
        first_mainchain_node_new_block = first_mainchain_node.getblock(block_hash)

        # SC node 1 and 2 forge 1 SC block
        sc_generate_blocks(first_sidechain_node)
        sc_generate_blocks(second_sidechain_node)

        # verify the block is included inside SC nodes 1 and 2
        first_sc_node_best_block = first_sidechain_node.block_best()["result"]
        second_sc_node_best_block = second_sidechain_node.block_best()["result"]
        third_sc_node_best_block = third_sidechain_node.block_best()["result"]

        assert_equal(first_sc_node_best_block["height"], 2, "The best block has not the specified height.")
        assert_equal(second_sc_node_best_block["height"], 2, "The best block has not the specified height.")
        assert_false(third_sc_node_best_block["height"]==2, "The best block has not the specified height.")

        sc_1_mc_block_inclusion = is_mainchain_block_included_in_sc_block(first_sc_node_best_block["block"],
                                                                               first_mainchain_node_new_block)
        sc_2_mc_block_inclusion = is_mainchain_block_included_in_sc_block(second_sc_node_best_block["block"],
                                                                               first_mainchain_node_new_block)
        assert_true(sc_1_mc_block_inclusion, "The mainchain block is not included for SC node 1.")
        assert_true(sc_2_mc_block_inclusion, "The mainchain block is not included for SC node 2.")

        # verify the mc block is NOT included inside SC node 3
        sc_3_mc_block_inclusion = is_mainchain_block_included_in_sc_block(third_sc_node_best_block["block"],
                                                                               first_mainchain_node_new_block)
        assert_false(sc_3_mc_block_inclusion, "The mainchain block is included for SC node 3.")

        first_sc_mc_best_block_ref_info = first_sidechain_node.mainchain_bestBlockReferenceInfo()["result"]
        second_sc_mc_best_block_ref_info = second_sidechain_node.mainchain_bestBlockReferenceInfo()["result"]
        third_sc_mc_best_block_ref_info = third_sidechain_node.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            is_mainchain_block_included_in_sidechain_block_reference_info(
                first_sc_mc_best_block_ref_info, first_mainchain_node_new_block),
            "The mainchain block is not included inside SC block reference info.")
        assert_true(
            is_mainchain_block_included_in_sidechain_block_reference_info(
                second_sc_mc_best_block_ref_info, first_mainchain_node_new_block),
            "The mainchain block is not included inside SC block reference info.")
        assert_false(
            is_mainchain_block_included_in_sidechain_block_reference_info(
                third_sc_mc_best_block_ref_info, first_mainchain_node_new_block),
            "The mainchain block is not included inside SC block reference info.")

        # connect MC 1 to MC 2
        connect_nodes_bi(self.nodes, 0, 1)
        self.sync_all()

        # connect SC 1 to SC 2 and SC 3
        connect_sc_nodes(self.sc_nodes[0], 1)
        connect_sc_nodes(self.sc_nodes[0], 2)
        self.sc_sync_all()

        # verify the block is included inside SC node 3
        third_sc_node_best_block = third_sidechain_node.block_best()["result"]
        sc_3_mc_block_inclusion = is_mainchain_block_included_in_sc_block(third_sc_node_best_block["block"], first_mainchain_node_new_block)
        assert_true(sc_3_mc_block_inclusion, "The mainchain block is not included for SC node 3.")

        # MC 2 mine a new block
        block_hash = second_mainchain_node.generate(1)[0]
        self.sync_all()
        second_mainchain_node_new_block = second_mainchain_node.getblock(block_hash)

        # SC node 3 forges 1 SC block
        sc_generate_blocks(third_sidechain_node)
        self.sc_sync_all()

        # verify the block is included inside SC nodes 1, 2 and 3
        first_sc_node_best_block = first_sidechain_node.block_best()["result"]
        second_sc_node_best_block = second_sidechain_node.block_best()["result"]
        third_sc_node_best_block = third_sidechain_node.block_best()["result"]

        assert_equal(first_sc_node_best_block["height"], 3, "The best block has not the specified height.")
        assert_equal(second_sc_node_best_block["height"], 3, "The best block has not the specified height.")
        assert_equal(third_sc_node_best_block["height"], 3, "The best block has not the specified height.")

        sc_1_mc_block_inclusion = is_mainchain_block_included_in_sc_block(first_sc_node_best_block["block"], second_mainchain_node_new_block)
        sc_2_mc_block_inclusion = is_mainchain_block_included_in_sc_block(second_sc_node_best_block["block"], second_mainchain_node_new_block)
        sc_3_mc_block_inclusion = is_mainchain_block_included_in_sc_block(third_sc_node_best_block["block"], second_mainchain_node_new_block)
        assert_true(sc_1_mc_block_inclusion, "The mainchain block is not included for SC node 1.")
        assert_true(sc_2_mc_block_inclusion, "The mainchain block is not included for SC node 2.")
        assert_true(sc_3_mc_block_inclusion, "The mainchain block is not included for SC node 3.")

        first_sc_mc_best_block_ref_info = first_sidechain_node.mainchain_bestBlockReferenceInfo()["result"]
        second_sc_mc_best_block_ref_info = second_sidechain_node.mainchain_bestBlockReferenceInfo()["result"]
        third_sc_mc_best_block_ref_info = third_sidechain_node.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            is_mainchain_block_included_in_sidechain_block_reference_info(
                first_sc_mc_best_block_ref_info, second_mainchain_node_new_block),
            "The mainchain block is not included inside SC block reference info.")
        assert_true(
            is_mainchain_block_included_in_sidechain_block_reference_info(
                second_sc_mc_best_block_ref_info, second_mainchain_node_new_block),
            "The mainchain block is not included inside SC block reference info.")
        assert_true(
            is_mainchain_block_included_in_sidechain_block_reference_info(
                third_sc_mc_best_block_ref_info, second_mainchain_node_new_block),
            "The mainchain block is not included inside SC block reference info.")


if __name__ == "__main__":
    MCSCConnectedNodes().main()