#!/usr/bin/env python3
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_true, assert_false, assert_equal, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, COIN
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, \
    assert_true, connect_sc_nodes
from SidechainTestFramework.sc_forging_util import *
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from httpCalls.wallet.balance import http_wallet_balance
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress, sendCointsToMultipleAddress
from httpCalls.transaction.allTransactions import allTransactions
"""
Check maximum mempool size on node
Configuration:
    Start 1 MC node and 2 SC node (with default websocket configuration).
    SC node1 connected to the first MC node 
    SC node2 connected to the first MC node and has mempool min fee rate parameter  set
Test:
    - Fill node1 with enough utxo
    - Add two tx on node1, one of them with  feerate below limit
    - Verify that on node1 both txs are present in mempool
    - Verify that on node2 the low rate tx is not present in mempool
"""
class SCMempoolMinFeeRate(SidechainTestFramework):

    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2
    mempool_min_fee_rate = 500

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10

    def setup_nodes(self):
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws',  '-logtimemicros=1', '-scproofqueuesize=0']] * self.number_of_mc_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        sc_node2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            mempool_min_fee_rate=self.mempool_min_fee_rate
        )
        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node, forward_amount=100, withdrawal_epoch_length=self.sc_withdrawal_epoch_length),
            sc_node_configuration,
            sc_node2_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]
        connect_sc_nodes(sc_node1, 1)  # Connect SC nodes
        mc_node1 = self.nodes[0]

        sc_address_1 = http_wallet_createPrivateKey25519(sc_node1)
        sc_address_2 = http_wallet_createPrivateKey25519(sc_node2)

        # Generate 1 SC block
        generate_next_blocks(sc_node1, "first node", 1)

        balanceBefore = http_wallet_balance(sc_node1)

        # We need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node1,
                                      sc_address_1,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node1.getnewaddress())
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        balanceAfter = http_wallet_balance(sc_node1)

        #check forward transfer arrived correctly
        assert_true((balanceAfter-balanceBefore) == (self.sc_nodes_bootstrap_info.genesis_account_balance * 100000000))

        #generate utxo on node1
        error = False
        try:
            sendCointsToMultipleAddress(sc_node1, [sc_address_1]*2, [10000]*2, 200)
        except:
            error = True
        assert_false(error)

        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        #add tx with low feerate
        error = False
        try:
            txLowId = sendCoinsToAddress(sc_node1, sc_address_2, 1000, 80)
            #this tx has size 174 and feerate 459
        except:
            error = True
        assert_false(error)

        #add tx with higher feerate
        error = False
        try:
            txHighId = sendCoinsToAddress(sc_node1, sc_address_2, 1000, 200)
            #this tx has size 174 and feerate 1149
        except:
            error = True
        assert_false(error)

        #a litte pause to let tx spread through the node mempools
        #note: we cannot use sync_sc_mempools since the mempools are different
        time.sleep(5)

        #check that on node2 the lowest fee transaction is not present in mempool
        memmpoolState = allTransactions(sc_node2, False)
        assert_true(len(memmpoolState['transactionIds']) == 1)
        assert_false(txLowId in memmpoolState['transactionIds'])
        assert_true(txHighId in memmpoolState['transactionIds'])

        #check that on node1 all txs are present
        memmpoolState = allTransactions(sc_node1, False)
        assert_true(len(memmpoolState['transactionIds']) == 2)
        assert_true(txLowId in memmpoolState['transactionIds'])
        assert_true(txHighId in memmpoolState['transactionIds'])


if __name__ == "__main__":
    SCMempoolMinFeeRate().main()