#!/usr/bin/env python3
import pprint
from datetime import datetime
import time

#import raw_input

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from sc_cert_fee_conf import EXPECTED_CERT_FEE
from test_framework.util import assert_equal, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, connect_nodes_bi, disconnect_nodes_bi
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
    connect_sc_nodes, assert_true, stop_sc_nodes, stop_sc_node, launch_db_tool, start_sc_node, \
    wait_for_sc_node_initialization
from SidechainTestFramework.sc_forging_util import *

"""
Test the offline db tool useful for interacting with the storage dbs 

Configuration:
    Start 1 MC nodes and 2 SC nodes .
    First SC1 node is connected to the MC node.
    Second SC2 Node is not connected to MC nor to first SC1 node

Test:
    - Synchronize MC node to the point of SC Creation Block.
    - SC1 forges 5 SC blocks
    - Connect SC2 node to the SC1 node so that it is aligned
    - Disconnect SC2 node and interact with its storages verifying the db tool behaviour   
    
    Order of storages update in a complete pmodModify cycle:
        1. History storage (block)
        ------------------------
        2. stateUtxoMerkleTree
        3. state
        4. stateForgerBox
        ------------------------
        5. wallet
        6. walletTransaction
        7. walletForgingStake
        8. walletCswDataStorage
        ------------------------
        9. History storage (block info validity)
       10. History storage (best block id)
    
"""

WITHDRAWAL_EPOCH_LENGTH = 10

class DBToolTest(SidechainTestFramework):

    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split = False):
        self.nodes = self.setup_nodes()
        self.sync_all()

    def setup_nodes(self):
        # Start MC node
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws',  '-logtimemicros=1', '-scproofqueuesize=0']] * 1)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node 1 connection to MC node 1
        mc_node_1 = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))), False)

        sc_node_2_configuration = SCNodeConfiguration(MCConnectionInfo(), False)

        self.network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_1_configuration, sc_node_2_configuration)
        bootstrap_sidechain_nodes(self.options, self.network, block_timestamp_rewind=2000*120)

    def sc_setup_nodes(self):
        # Start both SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):

        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        self.sync_all()

        stop_sc_node(1)

        genesis_sc_block_id = str(sc_node1.block_best()["result"]["block"]["id"])
        pprint.pprint("Genesis = " + genesis_sc_block_id)

        # Generate SC blocks
        NUM_BLOCKS = 5

        print("SC1 generates {} blocks".format(NUM_BLOCKS))
        blocks = generate_next_blocks(sc_node1, "first node", NUM_BLOCKS)

        storages_wallet = [
            "wallet",  "walletTransaction", "walletForgingStake",  "walletCswDataStorage"
        ]
        storages_state = [
            "state",   "stateForgerBox",    "stateUtxoMerkleTree"
        ]
        storages_history = [
            "history", "consensusData",    "secret"
        ]
        storages = []
        storages.extend(storages_history)
        storages.extend(storages_wallet)
        storages.extend(storages_state)



        # 1. Check that all storages versioned with blockid are consistent with genesis in SC node 2
        # all wallet and state but "walletforgingstake"
        storages_to_test = storages_wallet
        storages_to_test.remove("walletForgingStake")
        storages_to_test.extend(storages_state)

        for name in storages_to_test:
            json_params = {"storage":name}
            ret = launch_db_tool(sc_node2.cfgFileName, "lastVersionID", json_params)
            version = ret['version']
            print("{} --> {}".format(name, version))
            assert_equal(version, genesis_sc_block_id)

        # 2. Check that wallet forging stake has the same block id in the rollback versions and precisely
        # one commit behind
        listSize = 2
        json_params = {"storage": "walletForgingStake", "numberOfVersionToRetrieve":listSize}
        ret = launch_db_tool(sc_node2.cfgFileName, "versionsList", json_params)
        versionsList = ret["versionsList"]
        assert_true(genesis_sc_block_id in versionsList)
        assert_equal(genesis_sc_block_id, versionsList[-1])

        print("Starting SC2")
        start_sc_node(1, self.options.tmpdir)
        wait_for_sc_node_initialization(self.sc_nodes)

        print("Connecting SC2")
        connect_sc_nodes(self.sc_nodes[0], 1)

        print("Syncing...")
        T_0 = datetime.now()
        self.sc_sync_all()
        T_1 = datetime.now()
        u_sec = (T_1 - T_0).microseconds
        sec = (T_1 - T_0).seconds
        print("SC2 synced in {}.{} secs".format(sec, u_sec))

        assert_equal(sc_node1.block_best()["result"], sc_node2.block_best()["result"])
        stop_sc_node(1)

        # Check that all storages versioned with blockid are consistent with chainTip in SC node 2
        # all wallet and state, including "walletforgingstake"

        storages_to_test = storages_wallet
        storages_to_test.extend(storages_state)
        chainTipId = blocks[-1]

        for name in storages_to_test:
            json_params = {"storage":name}
            ret = launch_db_tool(sc_node2.cfgFileName, "lastVersionID", json_params)
            version = ret['version']
            print("{} --> {}".format(name, version))
            assert_equal(version, chainTipId)

        # -----------------------------------------------------------------------------------------
        # modify the history db rolling back the last two updates
        #   History storage (block info validity)
        #   History storage (best block id)
        # Then restart the node
        storages_to_test = []
        storages_to_test.append("history")
        listSize = 3
        json_params = {"storage": "history", "numberOfVersionToRetrieve":listSize}
        ret = launch_db_tool(sc_node2.cfgFileName, "versionsList", json_params)
        versionsList = ret["versionsList"]
        rollbackVersion = versionsList[-1]
        json_params = {"storage": "history", "versionToRollback": rollbackVersion}
        ret = launch_db_tool(sc_node2.cfgFileName, "rollback", json_params)
        assert_equal(ret["versionCurrent"], rollbackVersion)

        # generate one more block in SC1 because otherwise the sync will not happen, the reason is that SC2
        # has already processed the best block and it would not be re-processed again
        NUM_BLOCKS = 1
        print("SC1 generates {} block".format(NUM_BLOCKS))
        generate_next_blocks(sc_node1, "first node", NUM_BLOCKS)

        print("Starting SC2")
        start_sc_node(1, self.options.tmpdir)
        wait_for_sc_node_initialization(self.sc_nodes)

        print("Connecting SC2")
        connect_sc_nodes(self.sc_nodes[0], 1)

        print("Syncing...")
        T_0 = datetime.now()
        self.sc_sync_all()
        T_1 = datetime.now()
        u_sec = (T_1 - T_0).microseconds
        sec = (T_1 - T_0).seconds
        print("SC2 synced in {}.{} secs".format(sec, u_sec))

        assert_equal(sc_node1.block_best()["result"], sc_node2.block_best()["result"])
        stop_sc_node(1)

        input("_____________")

if __name__ == "__main__":
    DBToolTest().main()