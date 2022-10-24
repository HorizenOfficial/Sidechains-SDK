#!/usr/bin/env python3
import json
import logging
import os

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import initialize_default_sc_chain_clean, start_sc_nodes, \
    generate_next_blocks
from httpCalls.block.findBlockByID import http_block_findById
from test_framework.util import assert_equal, assert_true

"""
    Sets up 1 SC Node and tests the http Apis.
"""
class SidechainNodeApiTest(SidechainTestFramework):
    
    def add_options(self, parser):
        #empty implementation
        pass
    
    def setup_chain(self):
        pass
        
    def setup_network(self, split = False):
        pass
    
    def sc_setup_chain(self):
        initialize_default_sc_chain_clean(self.options.tmpdir, 1)
        
    def sc_setup_network(self, split = False):
        self.sc_nodes = self.sc_setup_nodes()

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def run_test(self):

        logging.info("Node initialization...")
        sc_node_name = "node0"
        sc_node = self.sc_nodes[0]

        #Node generates some blocks, then checking the best block height in chain
        logging.info("Generating new blocks...")
        blocks = generate_next_blocks(sc_node, sc_node_name, 3)
        logging.info("OK\n")


        logging.info("Getting the new best block...")
        sc_node_best_block_id = sc_node.block_best()["result"]["block"]["id"]
        sc_node_best_block_height = sc_node.block_best()["result"]["height"]
        logging.info("-->Node best block id: {0}".format(sc_node_best_block_id))
        logging.info("-->Node best block height: {0}".format(sc_node_best_block_height))

        logging.info("Checking that the height returned by findById api is the same returned by best api")
        logging.info("-->Calling findById API...")

        block_height = http_block_findById(sc_node,sc_node_best_block_id)["height"]
        logging.info("-->Node best block height from findById: {0}".format(block_height))

        assert_equal(sc_node_best_block_height, block_height, "The best block height returned from findById is not the same as the one from best.")
        logging.info("OK\n")

        logging.info("-->Calling findBlockInfoById API...")

        j = {
            "blockId": sc_node_best_block_id
        }
        request = json.dumps(j)
        block_info_result = sc_node.block_findBlockInfoById(request)

        assert_equal(block_height,block_info_result["result"]["blockInfo"]["height"])
        is_in_active_chain = block_info_result["result"]["isInActiveChain"]
        assert_true(is_in_active_chain, "The best block is not in the active chain.")
        logging.info("OK\n")

        logging.info("-->Calling storageVersions API...")
        storage_versions_result = sc_node.node_storageVersions()["result"]["listOfVersions"]
        logging.info(storage_versions_result)
        assert_equal(storage_versions_result["SidechainStateForgerBoxStorage"], sc_node_best_block_id)
        assert_equal(storage_versions_result["SidechainWalletBoxStorage"], sc_node_best_block_id)
        assert_equal(storage_versions_result["SidechainStateStorage"], sc_node_best_block_id)
        assert_equal(storage_versions_result["SidechainWalletTransactionStorage"], sc_node_best_block_id)
        assert_equal(storage_versions_result["ForgingBoxesInfoStorage"], sc_node_best_block_id)
        #SidechainStateUtxoMerkleTreeStorage and SidechainWalletCswDataStorage don't have a version, because they're not used without the CSW
        empty_version = ""
        assert_equal(storage_versions_result["SidechainStateUtxoMerkleTreeStorage"], empty_version)
        assert_equal(storage_versions_result["SidechainWalletCswDataStorage"], empty_version)

        #SidechainSecretStorage use random versions, we cannot predict them.
        #SidechainHistoryStorage use random versions, we cannot predict them.
        #ConsensusDataStorage use random versions, we cannot predict them.

        logging.info("OK\n")

        logging.info("-->Calling sidechainId API...")
        sidechain_id_result = sc_node.node_sidechainId()

        #Getting the sidechain_id from the sidechain configuration file
        with open(os.path.join(self.options.tmpdir, "sc_node0/node0.conf"), 'r') as config_file:
            tmp_config = config_file.readlines()
        line = ""
        for line in tmp_config:
            if "scId" in line:
                break

        assert_true("scId" in line, "Sidechain configuration file doesn't contain scId parameter.")
        sidechain_id = line.split('=')[1].strip(" \n\"")

        assert_equal(sidechain_id, sidechain_id_result["result"]["sidechainId"], "Returned sidechain_id is different from the one in the configuration file")
        logging.info("OK\n")


if __name__ == "__main__":
    SidechainNodeApiTest().main()
    