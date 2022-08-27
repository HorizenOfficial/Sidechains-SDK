#!/usr/bin/env python3
from curses import raw
import imp
import time
import pprint

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_1
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, connect_sc_nodes_bi, disconnect_sc_nodes_bi, \
    start_sc_nodes, generate_next_blocks, generate_next_block, connect_sc_nodes
from test_framework.util import assert_equal, assert_true, disconnect_nodes, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from httpCalls.block.findBlockByID import http_block_findById
from httpCalls.block.forgingInfo import http_block_forging_info
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress, sendCointsToMultipleAddress
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from httpCalls.transaction.broadcastMempool import http_broadcast_mempool
from httpCalls.wallet.allBoxes import http_wallet_allBoxes
"""
Configuration:
    Start 1 MC node and 2 SC node (with default websocket configuration).
    SC node connected to the first MC node.
    SC node has ENABLED certificate submitter.
    WithdrawalEpochLength = 11
    WithdrawalRequestBox slots open per MC block reference = 3999 / (11 - 1) = 399
"""

class ScBroadcastMempoolTest(SidechainTestFramework):

    sidechain_id = None
    sc_withdrawal_epoch_length = 11
    FEE = 5

    def setup_nodes(self):
        num_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws',  '-logtimemicros=1', '-scproofqueuesize=0']] * num_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            cert_submitter_enabled=True,  # enable submitter
            cert_signing_enabled=True,  # enable signer
        )
        sc_node_2_configuration = SCNodeConfiguration(
        MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
        cert_submitter_enabled=True,  # enable submitter
        cert_signing_enabled=True  # enable signer
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 1000, self.sc_withdrawal_epoch_length, sc_creation_version=SC_CREATION_VERSION_1, csw_enabled=True), sc_node_1_configuration, sc_node_2_configuration)
        self.sidechain_id = bootstrap_sidechain_nodes(self.options, network, 720*120*5).sidechain_id

    def sc_setup_nodes(self):
        return start_sc_nodes(2, self.options.tmpdir)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]
        connect_sc_nodes(sc_node1, 1)  # Connect SC nodes 1 and 2

        # create 1 FTs in the same MC block to SC
        sc_address_1 = http_wallet_createPrivateKey25519(sc_node1)
        ft_amount_1 = 100
        mc_return_address_1 = mc_node.getnewaddress()

        forward_transfer_to_sidechain(self.sidechain_id, mc_node,
                                      sc_address_1, ft_amount_1, mc_return_address_1)

        # Sleep for 1 second to let MC synchronize wallet
        time.sleep(1)
        self.sc_sync_all()

        # Generate 1 SC block to include FTs.
        generate_next_blocks(sc_node1, "first node", 1)[0]
        self.sc_sync_all()

        sc_address_2 = http_wallet_createPrivateKey25519(sc_node1)
        sendCointsToMultipleAddress(sc_node1,  [sc_address_2 for _ in range(10)], [1 for _ in range(10)], 0)
        self.sc_sync_all()

        disconnect_sc_nodes_bi(self.sc_nodes,0,1)

        pprint.pprint(http_wallet_allBoxes(sc_node1))
        for _ in range(0,10):
            sendCoinsToAddress(sc_node1, sc_address_1, 9, 0)

        assert_equal(len(allTransactions(sc_node1, True)["transactions"]),10)
        assert_equal(len(allTransactions(sc_node1, True)["transactions"]),0)

        #connect_sc_nodes_bi(self.sc_nodes,0,1)
        connect_sc_nodes(sc_node1, 1)

        http_broadcast_mempool(sc_node1)
        time.sleep(2)

        assert_equal(len(allTransactions(sc_node1, True)["transactions"]),10)
        assert_equal(len(allTransactions(sc_node1, True)["transactions"]),10)
    
        
if __name__ == "__main__":
    ScBroadcastMempoolTest().main()
