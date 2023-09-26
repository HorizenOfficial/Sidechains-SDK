#!/usr/bin/env python3
import os

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from test_framework.util import initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, AccountModel, \
    EVM_APP_BINARY
from SidechainTestFramework.sc_forging_util import *


"""
Check Latus forger behavior for:
1. Sidechain has multiple MC blocks to be synchronized (>50). Check that sidechains forging not fails due to 
   time out(SC create too many requests to MC for block generation).

Configuration:
    Start 1 MC nodes and 1 SC node.
    SC node connected to the first MC node.
    MC block reference delay is 1

Test:
    - Synchronize MC nodes to the point of SC Creation Block.
    - Mine 1 MC block, then forge 1 SC block, verify MC hash inclusion
    - Mine 200 MC blocks, then forge 5 SC blocks, verify MC data inclusion
     
     TODO In tests when SC is more than 50 blocks beyond MC Forger cannot retrieve more than 49 block headers
     for forging one block. Update this test after modifying Forger with bigger headers amount. 
"""


class MCSCEvmForging4(AccountChainSetup):

    inclusion_per_block = 48  # This value depends on amount of block hashes Sidechain can retrieve from the Mainchain
                              # in one request and MC Block Reference which reduces number of MC Headers to include

    def __init__(self):
        super().__init__(number_of_sidechain_nodes=1,
                         number_of_mc_nodes=1
                         )

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
        bootstrap_sidechain_nodes(self.options, network, model=AccountModel)

    def sc_setup_nodes(self):
        # Start 1 SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, dirname=self.options.tmpdir,
                              extra_args=[['-mc_block_delay_ref', '1']],
                              binary=[EVM_APP_BINARY] * self.number_of_sidechain_nodes
                              )

    def run_test(self):
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        # Generate MC block. Hash of this block won't be included into first SC block(scblock_id0)
        mcblock_id0 = mc_node.generate(1)[0]
        # Generate SC block
        scblock_id0 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcheaders_amount(0, scblock_id0, sc_node)
        check_mcreferencedata_amount(0, scblock_id0, sc_node)


        # Generate 200 MC blocks
        mcblock_hashes = mc_node.generate(200)
        included_mcblock_hashes = [mcblock_id0] + mcblock_hashes[:199]
        # Generate 5 SC blocks
        scblock_ids = generate_next_blocks(sc_node, "first node", 5)

        # Verify that SC block contains newly created MC blocks as MainchainHeaders and MainchainReferenceData
        # First 4 SC blocks. Every block contains 48 MainchainHeaders and 48 MainchainReferenceData
        for i in range(4):
            check_mcheaders_amount(self.inclusion_per_block, scblock_ids[i], sc_node)
            for mchash in included_mcblock_hashes[i * self.inclusion_per_block : (i + 1) * self.inclusion_per_block]:
                 check_mcheader_presence(mchash, scblock_ids[i], sc_node)
            check_mcreferencedata_amount(self.inclusion_per_block, scblock_ids[i], sc_node)
            for mchash in included_mcblock_hashes[i * self.inclusion_per_block : (i + 1) * self.inclusion_per_block]:
                 check_mcreferencedata_presence(mchash, scblock_ids[i], sc_node)

        # Fifth block. Contains 8 MainchainHeaders and 8 MainchainReferenceData
        check_mcheaders_amount(8, scblock_ids[4], sc_node)
        for mchash in included_mcblock_hashes[self.inclusion_per_block*4:200]:
            check_mcheader_presence(mchash, scblock_ids[4], sc_node)
        check_mcreferencedata_amount(8, scblock_ids[4], sc_node)
        for mchash in included_mcblock_hashes[self.inclusion_per_block*4:200]:
            check_mcreferencedata_presence(mchash, scblock_ids[4], sc_node)


if __name__ == "__main__":
    MCSCEvmForging4().main()
