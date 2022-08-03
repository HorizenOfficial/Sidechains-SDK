#!/usr/bin/env python3
import time
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_false, assert_true, initialize_chain_clean, start_nodes, websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import start_sc_nodes, generate_next_blocks, bootstrap_sidechain_nodes, connect_sc_nodes, check_wallet_coins_balance
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from httpCalls.wallet.exportSecret import http_wallet_exportSecret
from httpCalls.wallet.allPublicKeys import http_wallet_allPublicKeys
from httpCalls.wallet.importSecret import http_wallet_importSecret
from httpCalls.block.best import http_block_best, http_block_best_height
from httpCalls.block.findBlockByID import http_block_findById
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.wallet.balance import http_wallet_balance
from httpCalls.wallet.reindex import http_wallet_reindex, http_wallet_reindex_status, http_debug_reindex_step
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress


"""
    - Setup 2 SC Node.
    Part one - standard reindex usecase:
    - Create a new address on node1, send some zen to it and mine a block
    - Export the address key on node2, execute a reindex and check it detects the ZEN
    Part two - step by step reindex to check node behaviour
    - Start the reindex on node2
    - Check during reindex node2 can't send transactions
    
    
    - Create some new addresses on node1 and dump all its secret on file
    - Import the node1 secrets inside the node2 and verify that we imported only the secrets that were missing
"""
class SidechainWalletReindexTest(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2
    withdrawalEpochLength=10
    API_KEY_NODE1 = "aaaa"
    API_KEY_NODE2 = "Horizen2"

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split = False):
        # Setup nodes
        self.nodes = self.setup_nodes()
        self.sync_all()

    def setup_nodes(self):
        # Start 1 MC nodes
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node 1 and SC node 2 connection to MC node 1
        mc_node_1 = self.nodes[0]

        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            True,
            automatic_fee_computation=False,
            api_key=self.API_KEY_NODE1
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            True,
            automatic_fee_computation=False,
            api_key=self.API_KEY_NODE2
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, self.withdrawalEpochLength),
                                         sc_node_1_configuration,
                                         sc_node_2_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        # Start 2 SC nodes
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)
    
    def findAddress(self, propositions, address):
        for proposition in propositions:
            if (proposition['publicKey'] == address):
                return True
        return False
    
    def readFile(self, file_path):
        f = open(file_path, "r")
        key_list = []
        for line in f:
            if ("#" not in line):
                row = line.split(" ")
                key_list += [(row[0], row[1][:-1])]
        return key_list

    def run_test(self):
        self.sync_all()
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]
        connect_sc_nodes(sc_node1, 1)  # Connect SC nodes
        mc_node1 = self.nodes[0]

        sc_address_1 = http_wallet_createPrivateKey25519(sc_node1, self.API_KEY_NODE1)

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

        #Part one - "normal"  reindex flow

        print("# Generate 1 more address in sc_node1")
        sc_address_2 = http_wallet_createPrivateKey25519(sc_node1, self.API_KEY_NODE1)
        print("# Send some coins to the new address")
        sendCoinsToAddress(sc_node1, sc_address_2, 1000, 0, self.API_KEY_NODE1)

        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        firstSendToNode2Height = http_block_best_height(sc_node1)
        print(firstSendToNode2Height)

        print("# Node2 balance should be 0 at this point")
        balance_node2 = http_wallet_balance(sc_node2, self.API_KEY_NODE2)
        assert_equal(balance_node2, 0)

        print("# Import the key to node2")
        sc_secret_2 = http_wallet_exportSecret(sc_node1, sc_address_2, self.API_KEY_NODE1)
        http_wallet_importSecret(sc_node2, sc_secret_2, self.API_KEY_NODE2)
        print("# Check node2 now has the new address")
        pkeys_node2 = http_wallet_allPublicKeys(sc_node2, self.API_KEY_NODE2)
        assert_true(self.findAddress(pkeys_node2, sc_address_2))

        print("# Start reindex on node 2 and wait till it is completed")
        reindexStarted = http_wallet_reindex(sc_node2, self.API_KEY_NODE2)
        assert_equal(reindexStarted, True)
        reindexStatus = http_wallet_reindex_status(sc_node2, self.API_KEY_NODE2)
        while reindexStatus != 'inactive' :
            time.sleep(0.1)
            reindexStatus = http_wallet_reindex_status(sc_node2, self.API_KEY_NODE2)

        print("# Node2 balance should be changed now")
        balance_node2 = http_wallet_balance(sc_node2, self.API_KEY_NODE2)
        assert_equal(balance_node2, 1000)

        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        #Part two - perform step by step reindex to check how a node reacts during the reindex

        blockBest1 = http_block_best(sc_node1)

        print("# Start the reindex on node 2 (only first step)")
        http_debug_reindex_step(sc_node2, self.API_KEY_NODE2)

        print("# Check node 2 is on reindex status")
        reindexStatus_node2 = http_wallet_reindex_status(sc_node2, self.API_KEY_NODE2)
        assert_equal(reindexStatus_node2, 'ongoing')

        print("# Call some 'read only' endpoints to check they are still working")
        assert_equal(http_block_best(sc_node2), blockBest1)
        assert_equal(http_block_findById(sc_node2, blockBest1['id'])['block']['id'], blockBest1['id'])

        print("# Get balance on node2 is 0 since we just restarted the reindex")
        balance_node3 = http_wallet_balance(sc_node2, self.API_KEY_NODE2)
        assert_equal(balance_node3, 0)

        print("# Push a tx on the mempool from node1")
        sendCoinsToAddress(sc_node1, sc_address_2, 1000, 0, self.API_KEY_NODE1)

        print("# The tx should be present only on the node1 mempool")
        node1MempoolTx = allTransactions(sc_node1, False)
        assert_equal(len(node1MempoolTx['transactionIds']), 1)
        node2MempoolTx = allTransactions(sc_node2, False)
        assert_equal(len(node2MempoolTx['transactionIds']), 0)

        currentReindexHeight = 1
        while (currentReindexHeight < firstSendToNode2Height):
            assert_equal(http_wallet_balance(sc_node2, self.API_KEY_NODE2), 0)
            http_debug_reindex_step(sc_node2, self.API_KEY_NODE2)
            time.sleep(1)
            currentReindexHeight = currentReindexHeight + 1

        assert_equal(http_wallet_reindex_status(sc_node2, self.API_KEY_NODE2), 'ongoing')

        print("# At this step we should detected the send")
        assert_equal(http_wallet_balance(sc_node2, self.API_KEY_NODE2), 1000)

        print("# Pushing a tx from node2 generates an error during reindex")
        expectedTx = ''
        expectedTxMessage = ''
        try:
            sendCoinsToAddress(sc_node2, sc_address_1, 1000, 0, self.API_KEY_NODE2)
        except Exception as error:
            expectedTx = error.args[0]
            expectedTxMessage = error.args[1]

        assert_equal(http_wallet_balance(sc_node2, self.API_KEY_NODE2), 1000)

        assert_equal(expectedTx, 'GenericTransactionError')
        assert_equal(expectedTxMessage, 'Node reindex in progress - unable to send transaction')

        print("# Generate a  block on node 1")
        generate_next_blocks(sc_node1, "first node", 1)
        time.sleep(5)
        newHeightNode1 = http_block_best_height(sc_node1)
        newHeightNode2 = http_block_best_height(sc_node2)

        print("# Node 2 should not apply the new block while reindexing")
        assert_true(newHeightNode1 == (newHeightNode2 + 1))

        print("# Complete the reindex")
        reindexStatus_node2 = http_wallet_reindex_status(sc_node2, self.API_KEY_NODE2)
        while (reindexStatus_node2 == 'ongoing'):
            http_debug_reindex_step(sc_node2, self.API_KEY_NODE2)
            time.sleep(1)
            reindexStatus_node2 = http_wallet_reindex_status(sc_node2, self.API_KEY_NODE2)
        assert_equal(reindexStatus_node2, 'inactive')

        print(" Now we should now be able to post tx also on node2")
        sendCoinsToAddress(sc_node2, sc_address_1, 500, 0, self.API_KEY_NODE2)

        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        print("# Node 2 should have the block applied now")
        newHeightNode1Id = http_block_best(sc_node1)['id']
        newHeightNode2Id = http_block_best(sc_node2)['id']
        assert_true(newHeightNode1Id == newHeightNode2Id)




if __name__ == "__main__":
    SidechainWalletReindexTest().main()
