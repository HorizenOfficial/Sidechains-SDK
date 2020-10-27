#!/usr/bin/env python2

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from test_framework.util import initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks
from SidechainTestFramework.sc_forging_util import *


"""
Check Latus forger behavior for:
1. Sidechain forging not fails due to time out(SC create too many requests to MC for block generation).

Configuration:
    Start 1 MC nodes and 1 SC node.
    SC node connected to the first MC node.

Test:
    - Synchronize MC nodes to the point of SC Creation Block.
    - Mine 50 MC blocks, then forge SC block, verify MC data inclusion
    - Mine 150 MC blocks, then forge 2 SC blocks, verify MC data inclusion
    - Mine 200 MC blocks, then forge SC block, verify MC data inclusion  
     
     TODO In tests when SC is more than 50 blocks beyond MC Forger cannot retrieve more than 49 block headers
     for forging one block. Update this test after modifying Forger with bigger headers amount. 
"""


class MCSCForging4(SidechainTestFramework):

    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 1

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split = False):
        # Setup nodes and connect them
        self.nodes = self.setup_nodes()
        self.sync_all()

    def setup_nodes(self):
        # Start MC node
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node 1 connection to MC node 1
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 600, 1000),
                                         sc_node_configuration)
        bootstrap_sidechain_nodes(self.options.tmpdir, network)

    def sc_setup_nodes(self):
        # Start 1 SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]


        # Generate 50 MC blocks
        # Generate 1 SC block
        mcblock_hash1 = mc_node.generate(50)[0]
        scblock_id0 = generate_next_blocks(sc_node, "first node", 1)[0]
        # Verify that SC block contains newly created MC blocks as a MainchainHeaders and no MainchainRefData
        check_mcheaders_amount(49, scblock_id0, sc_node)
        check_mcreference_presence(mcblock_hash1, scblock_id0, sc_node)

        # Generate 150 MC blocks
        # Generate 2 SC blocks
        mc_node.generate(150)[0]
        scblock_id1 = generate_next_blocks(sc_node, "first node", 1)[0]
        # TODO Change amount of MC headers. At current implementation SC forger cannot retrieve more than 49 headers.
        # TODO Add MC reference presence
        check_mcheaders_amount(49, scblock_id1, sc_node)

        scblock_id2 = generate_next_blocks(sc_node, "first node", 2)[0]
        check_mcheaders_amount(49, scblock_id2, sc_node)

        # Generate 200 MC blocks
        # Generate 1 SC blocks
        mc_node.generate(200)[0]
        scblock_id3 = generate_next_blocks(sc_node, "first node", 4)[0]
        # TODO Change amount of MC headers. At current implementation SC forger cannot retrieve more than 49 headers.
        # TODO Add MC reference presence
        check_mcheaders_amount(49, scblock_id3, sc_node)

if __name__ == "__main__":
    MCSCForging4().main()
