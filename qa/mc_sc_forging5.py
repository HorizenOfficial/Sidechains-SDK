#!/usr/bin/env python3

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from httpCalls.wallet.allBoxesOfType import http_wallet_allBoxesOfType
from test_framework.util import assert_true, initialize_chain_clean, start_nodes, websocket_port_by_mc_node_index, \
    connect_nodes_bi, disconnect_nodes_bi, forward_transfer_to_sidechain, COIN, assert_not_equal
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_block
from SidechainTestFramework.sc_forging_util import *

"""
Check Latus forger behavior for:
1. The block with ommers doesn't extend the current Tip, but instead the block in the past.
   In this case Forger must skip Txs inclusion, because they are valid against the Tip,
   but can be invalid against the actual parent block. 
   

Configuration:
    Start 2 MC nodes and 1 SC node (with default websocket configuration).
    First SC node is connected to the first MC node.
    MC nodes are connected.

Test:
    - Synchronize MC nodes to the point of SC Creation Block.
    - Mine MC block on MC node 1 with FT to the SC node 1
    - Forge block on SC node 1 to be able to see the FT
    - Disconnect MC nodes.
    - Mine 1 more MC block on MC node 1.
    - Send Tx1 spending FT and creating ZenBox A. Then forge SC block respectively, verify Tx inclusion.
    - Send Tx2 spending ZenBox A.
    - Mine 2 MC blocks on MC node 2. Connect and synchronize MC nodes 1 and 2.
    - Forge SC block, verify that previously forged block was set as ommer and block has NO Txs (Tx2 was not included).
    - Verify Mempool: Tx1 was added again, Tx2 was removed as orphan.
    
    MC blocks on MC node 1 in the end:
    420     -   421[FT]     -   422
                    \
                        -   422'    -   423'*
            
            
    SC Block on SC node in the end: <sc block/slot number>[<mc headers included>; <mc refdata included>; <ommers>]
    G[420h;420d;] - 0[421h;421d;][FT] - 1[422h;422d;][TX1]                  MP[TX2]
                          \
                                            -   2[422'h,423'h;;1[...]]      MP[TX1]
"""


class MCSCForging5(SidechainTestFramework):
    number_of_mc_nodes = 2
    number_of_sidechain_nodes = 1

    def __init__(self):
        super().__init__()
        self.nodes = None
        self.sc_nodes_bootstrap_info = None

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split=False):
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

        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_1_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720*120*5)

    def sc_setup_nodes(self):
        # Start 1 SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        mc_node1 = self.nodes[0]
        mc_node2 = self.nodes[1]
        sc_node = self.sc_nodes[0]

        # Synchronize mc_node1 and mc_node2
        self.sync_all()

        # Do FT to SC node 1 and generate 1 MC block on the first MC node
        mc_return_address = mc_node1.getnewaddress()

        sc_address_1 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount = 10
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node1,
                                      sc_address_1,
                                      ft_amount,
                                      mc_return_address,
                                      generate_block=False)
        mc_node1.generate(1)
        scblock_id0 = generate_next_block(sc_node, "first node")

        # verify FT inclusion
        zen_boxes_1 = http_wallet_allBoxesOfType(sc_node, "ZenBox")
        assert_equal(1, len(zen_boxes_1), "Sidechain node expect to have 1 ZenBox - ForwardTransfer")
        assert_equal(ft_amount * COIN, zen_boxes_1[0]["value"],
                     "Sidechain node expect to have different ForwardTransfer amount")

        # Synchronize mc_node1 and mc_node2, then disconnect them.
        self.sync_all()
        disconnect_nodes_bi(self.nodes, 0, 1)

        # Generate 1 more MC block on MC node 1
        mcblock_hash1 = mc_node1.generate(1)[0]

        # Spend the FT, send it to ourselves.
        sc_address_2 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        tx1_id = sendCoinsToAddress(sc_node, sc_address_2, ft_amount * COIN, fee=0)
        scblock_id1 = generate_next_block(sc_node, "first node")

        # Check that FT was spent, and we still have only one ZenBox
        zen_boxes_2 = http_wallet_allBoxesOfType(sc_node, "ZenBox")
        assert_equal(1, len(zen_boxes_2), "Sidechain node expect to have 1 ZenBox - ForwardTransfer")
        assert_equal(ft_amount * COIN, zen_boxes_2[0]["value"],
                     "Sidechain node expect to have different ForwardTransfer amount")
        assert_not_equal(zen_boxes_1, zen_boxes_2, "Box was not spent")

        # Spend TX1 output and by TX2.
        sc_address_3 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        tx2_id = sendCoinsToAddress(sc_node, sc_address_3, ft_amount * COIN, fee=0)

        # Check that SC node mempool contains only TX2
        mempool_tx_ids = allTransactions(sc_node, False)['transactionIds']
        assert_equal(1, len(mempool_tx_ids), "Different mempool size found")
        assert_true(tx2_id in mempool_tx_ids)

        # Generate another 2 MC blocks on the second MC node
        fork_mcblock_hash1 = mc_node2.generate(1)[0]
        fork_mcblock_hash2 = mc_node2.generate(1)[0]

        # Connect and synchronize MC node 1 to MC node 2
        connect_nodes_bi(self.nodes, 0, 1)
        self.sync_all()
        # MC Node 1 should replace mcblock_hash1 Tip with [fork_mcblock_hash1, fork_mcblock_hash2]
        assert_equal(fork_mcblock_hash2, mc_node1.getbestblockhash())

        # Generate 1 SC block
        # SC block must contain fork_mcblock_hash1 and fork_mcblock_hash2
        # Ommered block also contains common mcblock_hash1
        scblock_id2 = generate_next_block(sc_node, "first node")
        check_scparent(scblock_id0, scblock_id2, sc_node)

        # Verify that SC block contains newly created MC blocks as a MainchainHeaders and no MainchainRefData
        check_mcheaders_amount(2, scblock_id2, sc_node)
        check_mcreferencedata_amount(0, scblock_id2, sc_node)
        check_mcheader_presence(fork_mcblock_hash1, scblock_id2, sc_node)
        check_mcheader_presence(fork_mcblock_hash2, scblock_id2, sc_node)

        # Verify that SC block contains 1 Ommer with 1 MainchainHeader
        check_ommers_amount(1, scblock_id2, sc_node)
        check_ommers_cumulative_score(1, scblock_id2, sc_node)
        check_ommer(scblock_id1, [mcblock_hash1], scblock_id2, sc_node)

        # Verify that SC doesn't contain transactions
        scblock_2 = sc_node.block_findById(blockId=scblock_id2)["result"]["block"]
        assert_equal(0, len(scblock_2["sidechainTransactions"]), "No txs expected in a block with ommers")

        # Check that SC node Mempool contains only TX1
        mempool_tx_ids = allTransactions(sc_node, False)['transactionIds']
        assert_equal(1, len(mempool_tx_ids), "Different mempool size found")
        assert_true(tx1_id in mempool_tx_ids)


if __name__ == "__main__":
    MCSCForging5().main()
