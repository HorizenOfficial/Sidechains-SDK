#!/usr/bin/env python3
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true
from SidechainTestFramework.scutil import initialize_default_sc_chain_clean, start_sc_nodes, \
                                         generate_next_blocks
from httpCalls.block.findBlockByID import http_block_findById
import json


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
        
        print("Node initialization...")
        sc_node_name = "node0"
        sc_node = self.sc_nodes[0]

        #Node generates some blocks, then checking the best block height in chain
        print("Generating new blocks...")
        blocks = generate_next_blocks(sc_node, sc_node_name, 3)
        print("OK\n")
        
        print("Getting the new best block...")
        sc_node_best_block_id = sc_node.block_best()["result"]["block"]["id"]
        sc_node_best_block_height = sc_node.block_best()["result"]["height"]
        print("-->Node best block id: {0}".format(sc_node_best_block_id))
        print("-->Node best block height: {0}".format(sc_node_best_block_height))

        print("Checking that the height returned by findById api is the same returned by best api")
        print("-->Calling findById...")
        block_height = http_block_findById(sc_node,sc_node_best_block_id)["height"]
        print("-->Node best block height from findById: {0}".format(block_height))

        assert_equal(sc_node_best_block_height, block_height, "The best block height returned from findById is not the same as the one from best.")
        print("OK\n")

        print("-->Calling findBlockInfoById...")

        j = {
            "blockId": sc_node_best_block_id
        }
        request = json.dumps(j)
        block_info_result = sc_node.block_findBlockInfoById(request)

        assert_equal(block_height,block_info_result["result"]["block"]["height"])
        is_in_active_chain = block_info_result["result"]["isInActiveChain"]
        assert_true(is_in_active_chain, "The best block is not in the active chain.")
        print("OK\n")

        print("-->Calling storageVersions...")
        storage_versions_result = sc_node.node_storageVersions()
        print(storage_versions_result) #TODO needs a more meaningful test
        print("OK\n")



if __name__ == "__main__":
    SidechainNodeApiTest().main()
    