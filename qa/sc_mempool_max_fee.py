
#!/usr/bin/env python3
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, COIN
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, \
    assert_true, connect_sc_nodes
from SidechainTestFramework.sc_forging_util import *
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from httpCalls.transaction.findTransactionByID import http_transaction_findById

"""
Check forger txes sorting algorithm based on feerate.
Configuration:
    Start 1 MC node and 2 SC node (with default websocket configuration).
    SC node1 connected to the first MC node and has max_fee parameter.
    SC node2 connected to the first MC node and has no max_fee parameter.
Test:
    - Verify that SC node1 is not able to send a transaction with fee > max_fee
    - Verify that SC node1 is able to send a transaction with fee = max_fee
    - Verify that SC node1 is able to send a transaction with fee < max_fee
    
    - Verify that SC node2 is able to send a transaction with fee > node1 max_fee (node 2 doesn't have this limit)
    - Verify that SC node1 included the last transaction in its mempool even if it has fee > max_fee
"""
class SCMempoolMaxFee(SidechainTestFramework):

    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2
    max_fee = 10

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10

    def setup_nodes(self):
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws',  '-logtimemicros=1', '-scproofqueuesize=0']] * self.number_of_mc_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            max_fee=self.max_fee
        )
        sc_node2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
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

        # We need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node1,
                                      sc_address_1,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node1.getnewaddress())
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()


        # Test that we are not able to send a transaction with fee > max_fee in sc_node1
        print("# Test that we are not able to send a transaction with fee > max_fee in sc_node1")
        error = False
        try:
            sendCoinsToAddress(sc_node1, sc_address_2, 10, self.max_fee + 1)
        except:
            error = True
        assert_true(error)

        # Test that we are able to send a transaction with fee = max_fee in sc_node1
        print("# Test that we are able to send a transaction with fee = max_fee in sc_node1")
        sendCoinsToAddress(sc_node1, sc_address_2, 100, self.max_fee)
        self.sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()  


        # Test that we are able to send a transaction with fee < max_fee in sc_node1
        print("# Test that we are able to send a transaction with fee < max_fee in sc_node1")
        sendCoinsToAddress(sc_node1, sc_address_2, 100, self.max_fee - 1)
        self.sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        # Test that we are able to send a transaction with fee > max_fee in sc_node2 that didn't set the max_fee parameter
        print("# Test that we are able to send a transaction with fee > max_fee in sc_node2 that didn't set the max_fee parameter")
        txid = sendCoinsToAddress(sc_node2, sc_address_1, 10, self.max_fee + 1)
        self.sync_all()
        
        # Verify that the sc_node1 included the transaction even if it has fee > than its max_fee
        print("# Verify that the sc_node1 included the transaction even if it has fee > than its max_fee")
        node_1_mempool = http_transaction_findById(sc_node2, txid)
        node_2_mempool = http_transaction_findById(sc_node1, txid)
        assert(len(node_1_mempool) > 0)
        assert(len(node_2_mempool) > 0)
        assert_equal(node_1_mempool, node_2_mempool)

        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()




if __name__ == "__main__":
    SCMempoolMaxFee().main()