#!/usr/bin/env python3

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from test_framework.util import initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, connect_nodes_bi, disconnect_nodes_bi
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks
from SidechainTestFramework.sc_forging_util import *

"""
Check Latus forger behavior for:
1. Current SC Tip with no new MC data extension.
2. Current SC Tip with some new MC References extension.
3. Current SC Tip with some new MC Headers and/or MC Ref Data extension.
4. Not a SC Tip, because of MC fork, linear ommers inclusion.
5. Not a SC Tip, because of MC fork, recursive ommers inclusion.

Configuration:
    Start 3 MC nodes and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.
    MC nodes are connected.

Test:
    - Synchronize MC nodes to the point of SC Creation Block.
    - Disconnect MC nodes.
    - Forge SC block, verify that there is no MC Headers and Data, no ommers.
    - Mine MC block on MC node 1 and forge SC block respectively, verify MC data inclusion.
    - Mine 2 MC blocks on MC node 2. Connect and synchronize MC nodes 1 and 2.
    - Forge SC block, verify that previously forged block was set as ommer, verify MC data inclusion.
    - Mine 1 MC block in MC node 1.
    - Forge SC block, verify MC data inclusion.
    - Forge one more SC block, verify MC data inclusion.
    - Forge one more SC block, verify that there is no MC data, no ommers.
    - Mine 6 Mc block in MC node 3. Connect and synchronize MC node 1 and 3.
    - Forge SC block, verify that previously forged blocks were set as ommers, verify MC data inclusion.
    - Check SC node forging status
    
    MC blocks on MC node 1 in the end:
    420     -   421
        \
            -   421'    -   422'    -   423'    -   424'
          \
            -   421''   -   422''   -   423''   -   424''   -   425''*
            
            
    SC Block on SC node in the end: <sc block/slot number>[<mc headers included>; <mc refdata included>; <ommers>]
    G[420h;420d;] - 0[;;] - 1[421h;421d;]
                          \
                                - 2[421'h,422'h;;1[...]] - 3[423'h,424'h;421'd-424'd;] - 4[;;]
                            \
                                    -   5[421''h-425''h;;2[...;1],3[...],4[;;]] - 6[;421''-425'';]
"""


class MCSCForging1(SidechainTestFramework):

    number_of_mc_nodes = 3
    number_of_sidechain_nodes = 1

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split = False):
        # Setup nodes and connect them
        self.nodes = self.setup_nodes()
        connect_nodes_bi(self.nodes, 0, 1)
        connect_nodes_bi(self.nodes, 0, 2)
        self.sync_all()

    def setup_nodes(self):
        # Start 3 MC nodes
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node 1 connection to MC node 1
        mc_node_1 = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
        )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_1_configuration)
        bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        # Start 1 SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        # Synchronize mc_node1, mc_node2 and mc_node3, then disconnect them.
        self.sync_all()
        disconnect_nodes_bi(self.nodes, 0, 1)
        disconnect_nodes_bi(self.nodes, 0, 2)
        mc_node1 = self.nodes[0]
        mc_node2 = self.nodes[1]
        mc_node3 = self.nodes[2]
        sc_node1 = self.sc_nodes[0]


        # Test 1: Generate SC block, when all MC blocks already synchronized.
        # Generate 1 SC block
        scblock_id0 = generate_next_blocks(sc_node1, "first node", 1)[0]
        # Verify that SC block has no MC headers, ref data, ommers
        check_mcheaders_amount(0, scblock_id0, sc_node1)
        check_mcreferencedata_amount(0, scblock_id0, sc_node1)
        check_ommers_amount(0, scblock_id0, sc_node1)


        # Test 2: Generate SC block, when new MC blocks following the same Tip appear.
        # Generate 1 MC block on the first MC node
        mcblock_hash1 = mc_node1.generate(1)[0]
        # Generate 1 SC block
        scblock_id1 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_scparent(scblock_id0, scblock_id1, sc_node1)
        # Verify that SC block contains MC block as a MainchainReference
        check_mcheaders_amount(1, scblock_id1, sc_node1)
        check_mcreferencedata_amount(1, scblock_id1, sc_node1)
        check_mcreference_presence(mcblock_hash1, scblock_id1, sc_node1)
        check_ommers_amount(0, scblock_id1, sc_node1)


        # Test 3: Generate SC block, when new MC blocks following different Tip appear. Ommers expected.
        # Generate another 2 MC blocks on the second MC node
        fork_mcblock_hash1 = mc_node2.generate(1)[0]
        fork_mcblock_hash2 = mc_node2.generate(1)[0]

        # Connect and synchronize MC node 1 to MC node 2
        connect_nodes_bi(self.nodes, 0, 1)
        self.sync_nodes([mc_node1, mc_node2])
        # MC Node 1 should replace mcblock_hash1 Tip with [fork_mcblock_hash1, fork_mcblock_hash2]
        assert_equal(fork_mcblock_hash2, mc_node1.getbestblockhash())

        # Generate 1 SC block
        scblock_id2 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_scparent(scblock_id0, scblock_id2, sc_node1)
        # Verify that SC block contains newly created MC blocks as a MainchainHeaders and no MainchainRefData
        check_mcheaders_amount(2, scblock_id2, sc_node1)
        check_mcreferencedata_amount(0, scblock_id2, sc_node1)
        check_mcheader_presence(fork_mcblock_hash1, scblock_id2, sc_node1)
        check_mcheader_presence(fork_mcblock_hash2, scblock_id2, sc_node1)
        # Verify that SC block contains 1 Ommer with 1 MainchainHeader
        check_ommers_amount(1, scblock_id2, sc_node1)
        check_ommers_cumulative_score(1, scblock_id2, sc_node1)
        check_ommer(scblock_id1, [mcblock_hash1], scblock_id2, sc_node1)


        # Test 4: Generate SC block, when new MC blocks following the same Tip appear + 2 previous RefData expecting to be synchronized.
        # Generate 2 more mc blocks in MC node 1
        mcblock_hash3 = mc_node1.generate(1)[0]
        mcblock_hash4 = mc_node1.generate(1)[0]

        # Generate SC block
        scblock_id3 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_scparent(scblock_id2, scblock_id3, sc_node1)
        # Verify that SC block MainchainHeaders and MainchainRefData
        check_mcheaders_amount(2, scblock_id3, sc_node1)
        check_mcreferencedata_amount(4, scblock_id3, sc_node1)
        check_mcheader_presence(mcblock_hash3, scblock_id3, sc_node1)
        check_mcheader_presence(mcblock_hash4, scblock_id3, sc_node1)
        check_mcreferencedata_presence(fork_mcblock_hash1, scblock_id3, sc_node1)
        check_mcreferencedata_presence(fork_mcblock_hash2, scblock_id3, sc_node1)
        check_mcreferencedata_presence(mcblock_hash3, scblock_id3, sc_node1)
        check_mcreferencedata_presence(mcblock_hash4, scblock_id3, sc_node1)
        check_ommers_amount(0, scblock_id3, sc_node1)

        # Generate SC block with no MC data. Needed for further test
        scblock_id4 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_scparent(scblock_id3, scblock_id4, sc_node1)
        check_mcheaders_amount(0, scblock_id4, sc_node1)
        check_mcreferencedata_amount(0, scblock_id4, sc_node1)
        check_ommers_amount(0, scblock_id4, sc_node1)


        # Test 5: MC Node 3 generates MC blocks, that hust from sc creation tx containing block.
        #         After MC synchronization, SC node should create with recursive ommers.
        # Generate another 6 blocks on MC node 3
        another_fork_mcblocks_hashes = mc_node3.generate(5)
        another_fork_tip_hash = another_fork_mcblocks_hashes[-1]
        connect_nodes_bi(self.nodes, 0, 2)
        self.sync_all()
        # MC Node 1 should replace mcblock_hash4 Tip with another_fork_tip_hash
        assert_equal(another_fork_tip_hash, mc_node1.getbestblockhash())

        # Generate SC block
        scblock_id5 = generate_next_blocks(sc_node1, "first node", 1)[0]
        # print(json.dumps(sc_node1.block_findById(blockId=scblock_id5), indent=4))
        check_scparent(scblock_id0, scblock_id5, sc_node1)
        # Verify that SC block contains newly created MC blocks as a MainchainHeaders and no MainchainRefData
        check_mcheaders_amount(5, scblock_id5, sc_node1)
        check_mcreferencedata_amount(0, scblock_id5, sc_node1)
        for mchash in another_fork_mcblocks_hashes:
            check_mcheader_presence(mchash, scblock_id5, sc_node1)
        # Verify that SC block contains 3 Ommers
        check_ommers_amount(3, scblock_id5, sc_node1)
        # Verify Ommers cumulative score, that must also count 1 subommer
        check_ommers_cumulative_score(4, scblock_id5, sc_node1)
        expected_ommers_ids = [scblock_id2, scblock_id3, scblock_id4]
        for ommer_id in expected_ommers_ids:
            check_ommer(ommer_id, [], scblock_id5, sc_node1)
        check_subommer(scblock_id2, scblock_id1, [mcblock_hash1], scblock_id5, sc_node1)

        # Generate 1 more SC Block to sync MainchainRefData
        scblock_id6 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_scparent(scblock_id5, scblock_id6, sc_node1)
        # Verify that SC block contains newly created MC blocks as a MainchainRefData and no MainchainHeaders
        check_mcheaders_amount(0, scblock_id6, sc_node1)
        check_mcreferencedata_amount(5, scblock_id6, sc_node1)
        check_ommers_amount(0, scblock_id6, sc_node1)
        for mchash in another_fork_mcblocks_hashes:
            check_mcreferencedata_presence(mchash, scblock_id6, sc_node1)

        # Check SC node forging status
        # Auto forging is disabled.
        is_forging_enabled = sc_node1.block_forgingInfo()["result"]["forgingEnabled"]
        assert_equal(False, is_forging_enabled, "Automatic forging expected to be disabled.")
        # Enable forging
        if "result" not in sc_node1.block_startForging():
            fail("Was not able to start auto forging.")
        # Check the new status
        is_forging_enabled = sc_node1.block_forgingInfo()["result"]["forgingEnabled"]
        assert_equal(True, is_forging_enabled, "Automatic forging expected to be enabled.")


if __name__ == "__main__":
    MCSCForging1().main()