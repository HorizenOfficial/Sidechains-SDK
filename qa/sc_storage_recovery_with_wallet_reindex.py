#!/usr/bin/env python3
import time
from datetime import datetime

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
    connect_sc_nodes, assert_true, stop_sc_node, launch_db_tool, start_sc_node,  \
    wait_for_sc_node_initialization
from test_framework.util import assert_equal, initialize_chain_clean, forward_transfer_to_sidechain, start_nodes, \
    websocket_port_by_mc_node_index
from httpCalls.wallet.exportSecret import http_wallet_exportSecret
from httpCalls.wallet.allPublicKeys import http_wallet_allPublicKeys
from httpCalls.wallet.importSecret import http_wallet_importSecret
from httpCalls.block.best import http_block_best, http_block_best_height
from httpCalls.block.findBlockByID import http_block_findById
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.wallet.balance import http_wallet_balance
from httpCalls.wallet.reindex import http_wallet_reindex, http_wallet_reindex_status, http_debug_reindex_step
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519


"""
Test that the sidechain can recover after a crash that left the SC storages inconsistent. 
For testing this scenario, the offline db tool is used for interacting with the storage dbs.

Configuration:
    Start 1 MC nodes and 2 SC nodes.

Test:
    - Synchronize MC node to the point of SC Creation Block.
    - SC1 forges 5 SC blocks
    - get storage versions for SC2 and check that they are as expected in this stage (only genesis data)
    - Connect SC2 node to the SC1 node so that it gets aligned
    - Stop the SC2 node and try to misalign its storages in various way, reproducing the node behaviour in case of a
      node crash during the storages update verifying that, restarting the SC2 node and reconnecting to the network, it
      can recover the situation and achieve synchronization with the SC1
      
      
    Order of storages update in a complete pmodModify cycle:
        1. History storage (block)
        ------------------------
            application (appState1, appState2 storages)
        2. stateUtxoMerkleTree
        3. state
        4. stateForgerBox
        ------------------------
            application (appWallet1, appWallet2 storages)
        5. wallet
        6. walletTransaction
        7. walletForgingStake
        8. walletCswDataStorage
        ------------------------
        9. History storage (block info with validity attribute)
       10. History storage (best block id)
    
"""

WITHDRAWAL_EPOCH_LENGTH = 10
CUSTOM_STORAGE_NAMES = " "

def printStoragesVersion(node, storages_list):
    for name in storages_list:
        # get the last version of the storage
        json_params = {"storage":name, "numberOfVersionToRetrieve": 5}
        print(node.dataDir)
        ret = launch_db_tool(node.dataDir, CUSTOM_STORAGE_NAMES, "versionsList", json_params)
        version = ret['versionsList']
        print("{} --> {}".format(name, version))


def checkStoragesVersion(node, storages_list, expectedVersion):
    for name in storages_list:
        # get the last version of the storage
        json_params = {"storage": name}
        ret = launch_db_tool(node.dataDir, CUSTOM_STORAGE_NAMES, "lastVersionID", json_params)
        version = ret['version']
        #print("{} --> {}".format(name, version))
        # check we got the expected version
        assert_equal(version, expectedVersion)


def rollbackStorages(node, storages_list, numberOfVersionsToRollback):
    for name in storages_list:
        # get the version list up to the desired number
        json_params = {"storage": name, "numberOfVersionToRetrieve": numberOfVersionsToRollback}
        versionsList = launch_db_tool(node.dataDir, CUSTOM_STORAGE_NAMES, "versionsList", json_params)["versionsList"]

        # get the target version to rollback to
        rollbackVersion = versionsList[-1]
        json_params = {"storage": name, "versionToRollback": rollbackVersion}
        print("...Rollbacking storage \"{}\" to version {}".format(name, rollbackVersion))
        ret = launch_db_tool(node.dataDir, CUSTOM_STORAGE_NAMES, "rollback", json_params)
        #print("{} --> {}".format(name, rollbackVersion))
        # check that we did it correctly
        assert_equal(ret["versionCurrent"], rollbackVersion)


class StorageRecoveryWithReindexTest(SidechainTestFramework):
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

        #Part one

        print("# Start the reindex on node 2 (only first 2 steps)")
        http_debug_reindex_step(sc_node2, self.API_KEY_NODE2)
        http_debug_reindex_step(sc_node2, self.API_KEY_NODE2)

        print("# Check node 2 is on reindex status")
        reindexStatus_node2 = http_wallet_reindex_status(sc_node2, self.API_KEY_NODE2)
        assert_equal(reindexStatus_node2, 'ongoing')

        print("Stopping SC2")
        stop_sc_node(sc_node2, 1)
        time.sleep(5)

        storages_list = ["history"]
        #rollback only history - (the reindex index will be not synched)
        rollbackStorages(sc_node2, storages_list, 2)

        start_sc_node(1, self.options.tmpdir)
        wait_for_sc_node_initialization(self.sc_nodes)
        time.sleep(2)

        reindexStatus = http_wallet_reindex_status(sc_node2, self.API_KEY_NODE2)
        maxTries = 600 #maximum time of 1 minute more or less to complete the reindex
        while (reindexStatus != 'inactive' or maxTries < 0) :
            time.sleep(0.1)
            maxTries = maxTries - 1
            reindexStatus = http_wallet_reindex_status(sc_node2, self.API_KEY_NODE2)
        assert_equal(reindexStatus, 'inactive')

        print("# Node2 balance should be changed  now")
        balance_node2 = http_wallet_balance(sc_node2, self.API_KEY_NODE2)
        assert_equal(balance_node2, 1000)

        #Part two

        print("# Start the reindex on node 2 (only first 2 steps)")
        http_debug_reindex_step(sc_node2, self.API_KEY_NODE2)
        http_debug_reindex_step(sc_node2, self.API_KEY_NODE2)

        print("# Check node 2 is on reindex status")
        reindexStatus_node2 = http_wallet_reindex_status(sc_node2, self.API_KEY_NODE2)
        assert_equal(reindexStatus_node2, 'ongoing')

        print("Stopping SC2")
        stop_sc_node(sc_node2, 1)
        time.sleep(5)

        #rollback only history - (the reindex index will be not synched)
        rollbackStorages(sc_node2, ["history"], 2)
        rollbackStorages(sc_node2, ["wallet"], 1)

        start_sc_node(1, self.options.tmpdir)
        wait_for_sc_node_initialization(self.sc_nodes)
        time.sleep(2)

        reindexStatus = http_wallet_reindex_status(sc_node2, self.API_KEY_NODE2)
        maxTries = 600 #maximum time of 1 minute more or less to complete the reindex
        while (reindexStatus != 'inactive' or maxTries < 0) :
            time.sleep(0.1)
            maxTries = maxTries - 1
            reindexStatus = http_wallet_reindex_status(sc_node2, self.API_KEY_NODE2)
        assert_equal(reindexStatus, 'inactive')

        print("# Node2 balance should be changed  now")
        balance_node2 = http_wallet_balance(sc_node2, self.API_KEY_NODE2)
        assert_equal(balance_node2, 1000)


if __name__ == "__main__":
    StorageRecoveryWithReindexTest().main()
