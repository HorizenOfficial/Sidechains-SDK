#!/usr/bin/env python3
import time

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from httpCalls.wallet.allBoxesOfType import http_wallet_allBoxesOfType
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.utils import convertZenToWei, convertZenniesToWei
from test_framework.util import assert_true, initialize_chain_clean, start_nodes, websocket_port_by_mc_node_index, \
    connect_nodes_bi, disconnect_nodes_bi, forward_transfer_to_sidechain, COIN, assert_not_equal, assert_equal
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_block
from SidechainTestFramework.sc_forging_util import *

"""
Check forger behavior for:
1. The block with ommers doesn't extend the current Tip, but instead the block in the past.
   In this case Forger must skip Txs inclusion, because they are valid against the Tip,
   but can be invalid against the actual parent block. In addition, the block must contain
   the state root hash calculated starting from the branching point, not the one from the tip.
   

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


class SCEVMMCFork(AccountChainSetup):

    def __init__(self):
        super().__init__(number_of_mc_nodes=2)

    def setup_network(self, split=False):
        # Setup nodes and connect them
        self.nodes = self.setup_nodes()
        connect_nodes_bi(self.nodes, 0, 1)
        self.sync_all()

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def run_test(self):
        mc_node1 = self.nodes[0]
        mc_node2 = self.nodes[1]
        sc_node = self.sc_nodes[0]

        # Synchronize mc_node1 and mc_node2
        self.sync_all()

        # Do FT to SC node 1 and generate 1 MC block on the first MC node

        mc_address_1 = mc_node1.getnewaddress()
        sc_address_1 = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        ft_amount = 10
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node1,
                                      sc_address_1,
                                      ft_amount,
                                      mc_address_1,
                                      generate_block=False)
        time.sleep(2)
        mc_address_2 = mc_node2.getnewaddress()
        mc_node1.sendtoaddress(mc_address_2, 2)
        self.sync_all()
        mc_node1.generate(1)

        scblock_id0 = generate_next_block(sc_node, "first node")

        # verify FT inclusion
        initial_balance = http_wallet_balance(sc_node, sc_address_1)
        assert_equal(convertZenToWei(ft_amount), initial_balance)

        # Synchronize mc_node1 and mc_node2, then disconnect them.
        self.sync_all()
        disconnect_nodes_bi(self.nodes, 0, 1)

        # Generate 1 more MC block on MC node 1
        mcblock_hash1 = mc_node1.generate(1)[0]

        # Transfer some funds to another address.
        sc_address_2 = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        tx1 = createEIP1559Transaction(sc_node, fromAddress=sc_address_1, toAddress=sc_address_2,
                                     nonce=0, gasLimit=21000, value=convertZenToWei(1))

        scblock_id1 = generate_next_block(sc_node, "first node")


        # Generate another 2 MC blocks on the second MC node


        fork_mcblock_hash1 = mc_node2.generate(1)[0]
        time.sleep(1)
        #
        sc_address_3 = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        ft_amount_3 = 1
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node2,
                                      sc_address_3,
                                      ft_amount_3,
                                      mc_address_2,
                                      generate_block=False)
        time.sleep(1)
        fork_mcblock_hash2 = mc_node2.generate(1)[0]
        time.sleep(1)
        assert_equal(2, len(mc_node2.getblock(fork_mcblock_hash2)['tx']))

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

        # verify that FT is not included yet
        initial_balance = http_wallet_balance(sc_node, sc_address_3)
        assert_equal(0, initial_balance)

        # Check that tx1 is again in the mempool
        response = allTransactions(sc_node, False)
        assert_equal(1, len(response['transactionIds']))
        assert_true(tx1 in response['transactionIds'])

        # Generate an additional SC block
        scblock_id3 = generate_next_block(sc_node, "first node")

        # verify FT inclusion
        initial_balance = http_wallet_balance(sc_node, sc_address_3)
        assert_equal(convertZenToWei(ft_amount_3), initial_balance)


if __name__ == "__main__":
    SCEVMMCFork().main()
