
#!/usr/bin/env python3
import logging
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

from httpCalls.transaction.findTransactionByID import http_transaction_findById

"""
Check maximum mempool size on node
Configuration:
    Start 1 MC node and 2 SC node (with default websocket configuration).
    SC node1 connected to the first MC node and has mempool max size parameter  set to 1MB.
    SC node2 connected to the first MC node and has default mempool max size.
Test:
    - Fill node2 with enough utxo
    - Add one tx1 on node2 with low fee
    - Add N txs on node2 with higher fee until the 1MB limit is exceeded
    - Verify that SC node2 mempool contains all the txs.
    - Verify that in SC node1 mempool does not contain tx1.
"""
class SCMempoolMaxSize(SidechainTestFramework):

    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2
    mempool_max_size = 1 #megabyte

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10

    def setup_nodes(self):
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws',  '-logtimemicros=1', '-scproofqueuesize=0']] * self.number_of_mc_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            mempool_max_size=self.mempool_max_size
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

        #generate multiple utxso on node1
        sendCointsToMultipleAddress(sc_node1, [sc_address_1] * 6, [181000] * 6, 100)
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()
        for x in range(6):
            addresses = [sc_address_2] * 900
            amounts = [200] * 900
            error = False
            try:
                sendCointsToMultipleAddress(sc_node1, addresses, amounts, 100)
            except:
                error = True
            assert_false(error)

        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        mempool_max_size_bytes = self.mempool_max_size * 1024 * 1024
        mempool_used_size = 0

        #send tx1 with lowfee
        error = False
        lowestTxId = None
        try:
            lowestTxId = sendCointsToMultipleAddress(sc_node2, [sc_address_1]*8, [15]*8, 30)
        except:
            error = True
        assert_false(error)

        #check tx is present in mempool and extract its size
        mempool_used_size = mempool_used_size + http_transaction_findById(sc_node2, lowestTxId)['transaction']['size']

        #send N others tx with higher fee until upper limit is exceeded
        logging.info("Filling mempool until 1MB limit...")
        txInd = 0
        numInserted = 1
        while (mempool_used_size < mempool_max_size_bytes):
            error = False
            try:
                txId = sendCointsToMultipleAddress(sc_node2, [sc_address_1]*8, [15]*8, 50)
                mempool_used_size = mempool_used_size + http_transaction_findById(sc_node2, txId)['transaction']['size']
            except:
                error = True
            assert_false(error)
            txInd = txInd + 1
            numInserted = numInserted + 1
            if (txInd==100):
                logging.info("Inserted so far: "+str(numInserted)+ " txs - mempool size: "+str(mempool_used_size))
                txInd = 0
        logging.info("Inserted so far: "+str(numInserted)+ " txs - mempool size: "+str(mempool_used_size))

        #check node2 contains all the txs
        memmpoolState = allTransactions(sc_node2, False)
        assert_true(len(memmpoolState['transactionIds']) == numInserted)
        assert_true(lowestTxId in memmpoolState['transactionIds'])

        #check that on node1 the lowest fee transaction was evicted
        memmpoolState = allTransactions(sc_node1, False)
        assert_true(len(memmpoolState['transactionIds']) == (numInserted -1))
        assert_false(lowestTxId in memmpoolState['transactionIds'])

if __name__ == "__main__":
    SCMempoolMaxSize().main()