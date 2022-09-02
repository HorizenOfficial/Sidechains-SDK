#!/usr/bin/env python3

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from test_framework.util import initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks
from SidechainTestFramework.sc_forging_util import *


"""
Check Latus forger behavior for:
1. Sidechain has multiple MC blocks to be synchronized (>50). Check that sidechains forging not fails due to 
   time out(SC create too many requests to MC for block generation).

Configuration:
    Start 1 MC nodes and 1 SC node.
    SC node connected to the first MC node.

Test:
    - Synchronize MC nodes to the point of SC Creation Block.
    - Mine 200 MC blocks, then forge 5 SC blocks, verify MC data inclusion
     
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

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 600, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_configuration)
        bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        # Start 1 SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        # Generate 200 MC blocks
        mcblock_hashes = mc_node.generate(200)
        # Generate 5 SC blocks
        scblock_ids = generate_next_blocks(sc_node, "first node", 5)

        # Verify that SC block contains newly created MC blocks as MainchainHeaders and MainchainReferenceData
        # First 4 SC blocks. Every block contains 49 MainchainHeaders and 49 MainchainReferenceData
        for i in range(4):
            check_mcheaders_amount(49, scblock_ids[i], sc_node)
            for mchash in mcblock_hashes[i * 49 : (i + 1) * 49]:
                check_mcheader_presence(mchash, scblock_ids[i], sc_node)
            check_mcreferencedata_amount(49, scblock_ids[i], sc_node)
            for mchash in mcblock_hashes[i * 49 : (i + 1) * 49]:
                check_mcreferencedata_presence(mchash, scblock_ids[i], sc_node)

        # Fifth block. Contains 4 MainchainHeaders and 4 MainchainReferenceData
        check_mcheaders_amount(4, scblock_ids[4], sc_node)
        for mchash in mcblock_hashes[196:200]:
            check_mcheader_presence(mchash, scblock_ids[4], sc_node)
        check_mcreferencedata_amount(4, scblock_ids[4], sc_node)
        for mchash in mcblock_hashes[196:200]:
            check_mcreferencedata_presence(mchash, scblock_ids[4], sc_node)


if __name__ == "__main__":
    MCSCForging4().main()
