#!/usr/bin/env python3

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from test_framework.util import assert_equal, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, connect_nodes_bi, disconnect_nodes_bi
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
    connect_sc_nodes
from SidechainTestFramework.sc_forging_util import *

"""
Check Latus forger behavior for:
1. Ommer SidechainBlock that contains both actual MC Block refs and orphaned MC block refs.

Configuration:
    Start 2 MC nodes and 2 SC node (with default websocket configuration).
    First SC node is connected to the first MC node.
    Second SC Node is not connected to MC nor to first SC node
    MC nodes are connected.

Test:
    - Synchronize MC nodes to the point of SC Creation Block.
    - Forge SC block, verify that there is no MC Headers and Data, no ommers.
    - Generate 1 MC block #221 and synchronize MC nodes.
    - Disconnect MC nodes.
    - Mine MC block on MC node 1 and forge SC block respectively, verify MC data inclusion.
    - Mine 2 MC blocks on MC node 2. Connect and synchronize MC nodes 1 and 2.
    - Forge SC block, verify that previously forged block was set as ommer, verify that common MC block #221 was included as well
    - Connect second SC node to the first SC node
    - Make sure that second SC node correctly apply first SC node data
    
    MC blocks on MC node 1 in the end:
    420     -   421     -   422
                    \
                        -   422'    -   423'*
            
            
    SC Block on SC node in the end: <sc block/slot number>[<mc headers included>; <mc refdata included>; <ommers>]
    G[420h;420d;] - 0[;;] - 1[421h-422h;421d-422d;]
                          \
                                -   2[421h,422'h,423'h;;1[...]]
"""


class MCSCForging2(SidechainTestFramework):

    number_of_mc_nodes = 2
    number_of_sidechain_nodes = 2

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split = False):
        # Setup nodes and connect them
        self.nodes = self.setup_nodes()
        connect_nodes_bi(self.nodes, 0, 1)
        self.sync_all()

    def setup_nodes(self):
        # Start 2 MC nodes
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node 1 connection to MC node 1
        mc_node_1 = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
        )
        sc_node_2_configuration = SCNodeConfiguration(MCConnectionInfo(), False)

        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_1_configuration, sc_node_2_configuration)
        bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        # Start 1 SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        mc_node1 = self.nodes[0]
        mc_node2 = self.nodes[1]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        # Synchronize mc_node1 and mc_node2
        self.sync_all()

        genesis_sc_block_id = sc_node1.block_best()["result"]

        # Generate 1 SC block without any MC block info
        scblock_id0 = generate_next_blocks(sc_node1, "first node", 1)[0]
        # Verify that SC block has no MC headers, ref data, ommers
        check_mcheaders_amount(0, scblock_id0, sc_node1)
        check_mcreferencedata_amount(0, scblock_id0, sc_node1)
        check_ommers_amount(0, scblock_id0, sc_node1)

        # Generate 1 MC block on the first MC node
        mcblock_hash1 = mc_node1.generate(1)[0]
        # Synchronize mc_node1 and mc_node2, then disconnect them.
        self.sync_all()
        disconnect_nodes_bi(self.nodes, 0, 1)

        # Generate 1 more MC block on the first MC node
        mcblock_hash2 = mc_node1.generate(1)[0]

        # Generate 1 SC block, that should put 2 MC blocks inside
        # SC block contains MC `mcblock_hash1` that is common for MC Nodes 1,2 and `mcblock_hash2` that is known only by MC Node 1.
        scblock_id1 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_scparent(scblock_id0, scblock_id1, sc_node1)
        # Verify that SC block contains MC block as a MainchainReference
        check_mcheaders_amount(2, scblock_id1, sc_node1)
        check_mcreferencedata_amount(2, scblock_id1, sc_node1)
        check_mcreference_presence(mcblock_hash1, scblock_id1, sc_node1)
        check_mcreference_presence(mcblock_hash2, scblock_id1, sc_node1)
        check_ommers_amount(0, scblock_id1, sc_node1)

        # Generate another 2 MC blocks on the second MC node
        fork_mcblock_hash1 = mc_node2.generate(1)[0]
        fork_mcblock_hash2 = mc_node2.generate(1)[0]

        # Connect and synchronize MC node 1 to MC node 2
        connect_nodes_bi(self.nodes, 0, 1)
        self.sync_all()
        # MC Node 1 should replace mcblock_hash2 Tip with [fork_mcblock_hash1, fork_mcblock_hash2]
        assert_equal(fork_mcblock_hash2, mc_node1.getbestblockhash())

        # Generate 1 SC block
        # SC block must contains `mcblock_hash1` again and add fork_mcblock_hash1,2
        # Ommered block also contains common `mcblock_hash1`, but moreover an orphaned `mcblock_hash2`
        scblock_id2 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_scparent(scblock_id0, scblock_id2, sc_node1)
        # Verify that SC block contains newly created MC blocks as a MainchainHeaders and no MainchainRefData
        check_mcheaders_amount(3, scblock_id2, sc_node1)
        check_mcreferencedata_amount(0, scblock_id2, sc_node1)
        check_mcheader_presence(mcblock_hash1, scblock_id2, sc_node1)
        check_mcheader_presence(fork_mcblock_hash1, scblock_id2, sc_node1)
        check_mcheader_presence(fork_mcblock_hash2, scblock_id2, sc_node1)
        # Verify that SC block contains 1 Ommer with 1 MainchainHeader
        check_ommers_amount(1, scblock_id2, sc_node1)
        check_ommers_cumulative_score(1, scblock_id2, sc_node1)
        check_ommer(scblock_id1, [mcblock_hash1, mcblock_hash2], scblock_id2, sc_node1)

        assert_equal(genesis_sc_block_id, sc_node2.block_best()["result"])
        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()

        assert_equal(sc_node1.block_best()["result"], sc_node2.block_best()["result"])

if __name__ == "__main__":
    MCSCForging2().main()