#!/usr/bin/env python3
import logging
import time
from datetime import datetime

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
    connect_sc_nodes, assert_true, stop_sc_node, launch_db_tool, start_sc_node, \
    wait_for_sc_node_initialization
from test_framework.util import assert_equal, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index

# import raw_input

"""
Test that the sidechain can recover after a crash that left the SC storages inconsistent. 
For testing this scenario, the offline db tool is used for interacting with the storage dbs.
For this testing the CSW is enabled. 

Configuration:
    Start 1 MC nodes and 2 SC nodes.
    First SC1 node is connected to the MC node.
    Second SC2 Node is not connected to MC nor to first SC1 node
    CSW is enabled.

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
CUSTOM_STORAGE_NAMES = "appState1,appState2,appWallet1,appWallet2"


def checkStoragesVersion(node, storages_list, expectedVersion):
    for name in storages_list:
        # get the last version of the storage
        json_params = {"storage": name}
        ret = launch_db_tool(node.dataDir, CUSTOM_STORAGE_NAMES, "lastVersionID", json_params)
        version = ret['version']
        #logging.info("{} --> {}".format(name, version))
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
        logging.info("...Rollbacking storage \"{}\" to version {}".format(name, rollbackVersion))
        ret = launch_db_tool(node.dataDir, CUSTOM_STORAGE_NAMES, "rollback", json_params)
        #logging.info("{} --> {}".format(name, rollbackVersion))

        # check that we did it correctly
        assert_equal(ret["versionCurrent"], rollbackVersion)


class StorageRecoveryWithCSWTest(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split=False):
        self.nodes = self.setup_nodes()
        self.sync_all()

    def setup_nodes(self):
        # Start MC node
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir,
                           extra_args=[['-debug=sc', '-debug=ws', '-logtimemicros=1', '-scproofqueuesize=0']])

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node 1 connection to MC node 1
        mc_node_1 = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            False)

        sc_node_2_configuration = SCNodeConfiguration(MCConnectionInfo(), False)

        self.network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, WITHDRAWAL_EPOCH_LENGTH, csw_enabled=True),
                                              sc_node_1_configuration, sc_node_2_configuration)
        bootstrap_sidechain_nodes(self.options, self.network, block_timestamp_rewind=2000 * 120)

    def sc_setup_nodes(self):
        # Start both SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def startAndSyncScNode2(self):
        logging.info("Starting SC2")
        start_sc_node(1, self.options.tmpdir)
        wait_for_sc_node_initialization(self.sc_nodes)
        time.sleep(2)

        logging.info("Connecting SC2")
        connect_sc_nodes(self.sc_nodes[0], 1)

        logging.info("Syncing...")
        T_0 = datetime.now()
        self.sc_sync_all()
        T_1 = datetime.now()
        u_sec = (T_1 - T_0).microseconds
        sec = (T_1 - T_0).seconds
        logging.info("...SC2 synced in {}.{} secs".format(sec, u_sec))

    def forgeBlockAndCheckSync(self):

        # generate one more block in SC1 because otherwise the sync will not happen, the reason is that SC2
        # has already processed the best block and it would not be re-processed again
        NUM_BLOCKS = 1
        logging.info("SC1 generates {} block".format(NUM_BLOCKS))
        self.blocks.extend(generate_next_blocks(self.sc_nodes[0], "first node", NUM_BLOCKS))
        time.sleep(2)

        self.startAndSyncScNode2()

        assert_equal(self.sc_nodes[0].block_best()["result"], self.sc_nodes[1].block_best()["result"])
        logging.info("Stopping SC2")
        stop_sc_node(self.sc_nodes[1], 1)
        time.sleep(1)

        # Check that all storages versioned with blockid are consistent with chainTip in SC node 2
        storages_list = ["wallet", "walletTransaction", "walletForgingStake", "walletCswDataStorage",
                            "stateUtxoMerkleTree", "state", "stateForgerBox"]
        chainTipId = self.blocks[-1]
        checkStoragesVersion(self.sc_nodes[1], storages_list, chainTipId)

    def run_test(self):

        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        self.blocks = []
        self.sync_all()

        logging.info("Stopping SC2")
        stop_sc_node(sc_node2, 1)

        genesis_sc_block_id = str(sc_node1.block_best()["result"]["block"]["id"])

        # Generate SC blocks
        NUM_BLOCKS = 5
        logging.info("SC1 generates {} blocks...".format(NUM_BLOCKS))
        self.blocks.extend(generate_next_blocks(sc_node1, "first node", NUM_BLOCKS))

        logging.info("Test 0 ######")
        # Check that in stopped node SC2 all storages versioned with blockid (but "walletForgingStake")
        # are consistent with genesis block id
        storages_list = ["wallet", "walletTransaction", "walletCswDataStorage",
                         "stateUtxoMerkleTree", "state", "stateForgerBox"]
        checkStoragesVersion(sc_node2, storages_list, genesis_sc_block_id)

        # Check that wallet forging stake has the same block id in the rollback versions and precisely
        # one commit behind
        json_params = {"storage": "walletForgingStake", "numberOfVersionToRetrieve": 2}
        versionsList = launch_db_tool(sc_node2.dataDir, CUSTOM_STORAGE_NAMES, "versionsList", json_params)["versionsList"]
        assert_true(genesis_sc_block_id in versionsList)
        assert_equal(genesis_sc_block_id, versionsList[-1])

        self.startAndSyncScNode2()

        assert_equal(sc_node1.block_best()["result"], sc_node2.block_best()["result"])
        logging.info("Stopping SC2")
        stop_sc_node(sc_node2, 1)

        logging.info("Test 10 ######")
        storages_list = ["history"]
        rollbackStorages(sc_node2, storages_list, 2)
        self.forgeBlockAndCheckSync()

        logging.info("Test 9 ######")
        storages_list = ["history"]
        rollbackStorages(sc_node2, storages_list, 3)
        self.forgeBlockAndCheckSync()

        logging.info("Test 8 ######")
        storages_list = ["history"]
        rollbackStorages(sc_node2, storages_list, 3)
        storages_list = ["walletCswDataStorage"]
        rollbackStorages(sc_node2, storages_list, 2)
        self.forgeBlockAndCheckSync()

        logging.info("Test 7 ######")
        storages_list = ["history"]
        rollbackStorages(sc_node2, storages_list, 3)
        storages_list = ["walletForgingStake", "walletCswDataStorage"]
        rollbackStorages(sc_node2, storages_list, 2)
        self.forgeBlockAndCheckSync()

        logging.info("Test 6 ######")
        storages_list = ["history"]
        rollbackStorages(sc_node2, storages_list, 3)
        storages_list = ["walletTransaction", "walletForgingStake", "walletCswDataStorage"]
        rollbackStorages(sc_node2, storages_list, 2)
        self.forgeBlockAndCheckSync()

        logging.info("Test 5 ######")
        storages_list = ["history"]
        rollbackStorages(sc_node2, storages_list, 3)
        storages_list = ["wallet", "walletTransaction", "walletForgingStake", "walletCswDataStorage"]
        rollbackStorages(sc_node2, storages_list, 2)
        self.forgeBlockAndCheckSync()

        logging.info("Test application wallet misalignment ######")
        storages_list = ["history"]
        rollbackStorages(sc_node2, storages_list, 3)
        storages_list = ["appWallet2", "wallet", "walletTransaction", "walletForgingStake", "walletCswDataStorage"]
        rollbackStorages(sc_node2, storages_list, 2)
        self.forgeBlockAndCheckSync()

        logging.info("Test 4 ######")
        storages_list = ["history"]
        rollbackStorages(sc_node2, storages_list, 3)
        storages_list = ["stateForgerBox",
                         "appWallet1", "appWallet2",
                         "wallet", "walletTransaction", "walletForgingStake", "walletCswDataStorage"]
        rollbackStorages(sc_node2, storages_list, 2)
        self.forgeBlockAndCheckSync()

        logging.info("Test 3 ######")
        storages_list = ["history"]
        rollbackStorages(sc_node2, storages_list, 3)
        storages_list = ["state", "stateForgerBox",
                         "appWallet1", "appWallet2",
                         "wallet", "walletTransaction", "walletForgingStake", "walletCswDataStorage"]
        rollbackStorages(sc_node2, storages_list, 2)
        self.forgeBlockAndCheckSync()

        logging.info("Test 2 ######")
        storages_list = ["history"]
        rollbackStorages(sc_node2, storages_list, 3)
        storages_list = ["stateUtxoMerkleTree", "state", "stateForgerBox",
                         "appWallet1", "appWallet2",
                         "wallet", "walletTransaction", "walletForgingStake", "walletCswDataStorage"]
        rollbackStorages(sc_node2, storages_list, 2)
        self.forgeBlockAndCheckSync()

        logging.info("Test app state misalignment ######")
        storages_list = ["history"]
        rollbackStorages(sc_node2, storages_list, 3)
        # rollback just one of the two app storages (simple app has app1 and app2 storages)
        storages_list = ["appState2",
                         "stateUtxoMerkleTree", "state", "stateForgerBox",
                         "appWallet1", "appWallet2",
                         "wallet", "walletTransaction", "walletForgingStake", "walletCswDataStorage"]
        rollbackStorages(sc_node2, storages_list, 2)
        self.forgeBlockAndCheckSync()

        logging.info("Test 1 ######")
        storages_list = ["history"]
        rollbackStorages(sc_node2, storages_list, 4)
        storages_list = ["appState1", "appState2",
                         "stateUtxoMerkleTree", "state", "stateForgerBox",
                         "appWallet1", "appWallet2",
                         "wallet", "walletTransaction", "walletForgingStake", "walletCswDataStorage"]
        rollbackStorages(sc_node2, storages_list, 2)
        self.forgeBlockAndCheckSync()

        self.startAndSyncScNode2()
        # reach the end of consensus epoch
        h = len(self.blocks) + 1
        logging.info("Reaching end of consensus epoch, currently at bloch height {}...".format(h))
        NUM_BLOCKS = 722 - h
        logging.info("SC1 generates {} blocks...".format(NUM_BLOCKS))
        self.blocks.extend(generate_next_blocks(sc_node1, "first node", NUM_BLOCKS, verbose=False))
        self.sc_sync_all()

        assert_equal(sc_node1.block_best()["result"], sc_node2.block_best()["result"])
        stop_sc_node(sc_node2, 1)

        logging.info("Test end consensus epoch ######")
        # -----------------------------------------------------------------------------------------
        # Modify history state and wallet storages and verify that we are able to recover also at
        # the edge of consensus epoch switch
        storages_list = ["history"]
        rollbackStorages(sc_node2, storages_list, 3)
        storages_list = ["appState1", "appState2",
                         "stateUtxoMerkleTree", "state", "stateForgerBox",
                         "appWallet1", "appWallet2",
                         "wallet", "walletTransaction", "walletForgingStake", "walletCswDataStorage"]
        rollbackStorages(sc_node2, storages_list, 2)

        # Check that wallet forging stake has the same blockid version of the wallet in its rollback versions
        # and is precisely one commit ahead since we reached the consensus epoch change block
        rolbackBlockVersionId = self.blocks[-2]
        # --wallet has been rollbacked to the previous block:
        checkStoragesVersion(sc_node2, ["wallet"], rolbackBlockVersionId)

        json_params = {"storage": "walletForgingStake", "numberOfVersionToRetrieve": 2}
        versionsList = launch_db_tool(sc_node2.dataDir, CUSTOM_STORAGE_NAMES, "versionsList", json_params)["versionsList"]
        assert_true(rolbackBlockVersionId in versionsList)
        # -- wallet forging stake is ahead by one version
        assert_equal(rolbackBlockVersionId, versionsList[-1])


        self.forgeBlockAndCheckSync()


        logging.info("Test negative ######")
        # reproduce an unexpected inconsistency and check we are not able to recover
        storages_list = ["history"]
        rollbackStorages(sc_node2, storages_list, 7)

        storages_list = ["stateUtxoMerkleTree", "walletCswDataStorage"]
        rollbackStorages(sc_node2, storages_list, 5)
        storages_list = ["stateForgerBox", "walletCswDataStorage"]
        rollbackStorages(sc_node2, storages_list, 2)
        storages_list = ["state", "wallet", "walletTransaction", "walletForgingStake"]
        rollbackStorages(sc_node2, storages_list, 13)

        exception_thrown = False
        try:
            # restart SC2
            self.forgeBlockAndCheckSync()
        except Exception as e:
            exception_thrown = True
            logging.info("Expected exception caught during negative testing: " + str(e))
            logging.info("Stopping SC2")
            stop_sc_node(sc_node2, 1)
        finally:
            assert_true(exception_thrown, "Exception should have been thrown")


if __name__ == "__main__":
    StorageRecoveryWithCSWTest().main()
