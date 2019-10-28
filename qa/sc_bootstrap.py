#!/usr/bin/env python2
import json

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true

"""
    Bootstrap 3 SC Nodes and start them with genesis info extracted from a mainchain node.
    
    - Load a MC node
    - Mine some blocks to reach hard fork
    - Create 3 SC nodes
    - Extract genesis info
    - Start SC nodes with that genesis info
    
    For each SC node verify:
    - check all keys/boxes/balances are coherent with the default initialization
    - verify MC block reference's inclusion
"""
class SCBootstrap(SidechainTestFramework):

    def sc_setup_chain(self):
        # SC chain setup
        pass

    def sc_setup_network(self, split=False):
        # SC network setup
        pass

    def check_genesis_balances(self, sc_node, sidechain_id, expected_keys_count, expected_boxes_count, expected_wallet_balance):
        print("Genesis checks for sidechain id {0}.".format(sidechain_id))
        print("Checking that each public key has a box assigned with a non-zero value.")
        response = sc_node.wallet_allPublicKeys()
        public_keys = response["result"]["propositions"]
        response = sc_node.wallet_allBoxes()
        boxes = response["result"]["boxes"]
        response = sc_node.wallet_balance()
        balance = response["result"]
        assert_equal(expected_keys_count, len(public_keys), "Unexpected number of public keys")
        assert_equal(expected_boxes_count, len(boxes), "Unexpected number of boxes")
        for key in public_keys:
            target = None
            for box in boxes:
                if box["proposition"]["publicKey"] == key["publicKey"]:
                    target = box
                    assert_true(box["value"] > 0, "Non positive value for box: {0} with public key: {1}".format(box["id"], key))
                    break
            assert_true(target is not None, "Box related to public key: {0} not found".format(key))
        print("Checking genesis balance.")
        assert_equal(expected_wallet_balance, int(balance["balance"]), "Unexpected balance")
        print("Total balance: {0}".format(json.dumps(balance["balance"])))

    def check_mainchan_block_inclusion(self, sc_node, sidechain_id, expected_sc_block_height, mc_block, keys):
        print("Check genesis block for sidechain id {0}.".format(sidechain_id))
        response = sc_node.block_best()
        height = response["result"]["height"]
        assert_equal(expected_sc_block_height, height, "The best block is not the genesis block.")
        mc_block_json = response["result"]["block"]["mainchainBlocks"][0]
        new_boxes = mc_block_json["sidechainRelatedAggregatedTransaction"]["newBoxes"]
        print("Checking that each public key has a box assigned with a non-zero value.")
        for key in keys:
            target = None
            for box in new_boxes:
                if box["proposition"]["publicKey"] == key["publicKey"]:
                    target = box
                    box_value = box["value"]
                    assert_true(box_value > 0, "Non positive value for box: {0} with public key: {1}".format(box["id"], key))
                    assert_equal(100*100000000, box_value, "Unexpected value for box: {0} with public key: {1}".format(box["id"], key))
                    break
            assert_true(target is not None, "Box related to public key: {0} not found".format(key))

        print("Checking mainchain block reference inclusion.")
        mc_block_version = mc_block["version"]
        mc_block_merkleroot = mc_block["merkleroot"]
        mc_block_time = mc_block["time"]
        mc_block_bits = mc_block["bits"]
        mc_block_nonce = mc_block["nonce"]
        mc_block_previousblockhash = mc_block["previousblockhash"]
        sc_mc_block_version = mc_block_json["header"]["version"]
        sc_mc_block_merkleroot = mc_block_json["header"]["hashMerkleRoot"]
        sc_mc_block_time = mc_block_json["header"]["time"]
        sc_mc_block_bits = mc_block_json["header"]["bits"]
        sc_mc_block_nonce = mc_block_json["header"]["nonce"]
        sc_mc_block_previousblockhash = mc_block_json["header"]["hashPrevBlock"]
        assert_equal(mc_block_version, sc_mc_block_version)
        assert_equal(mc_block_merkleroot, sc_mc_block_merkleroot)
        assert_equal(mc_block_time, sc_mc_block_time)
        assert_equal(mc_block_nonce, sc_mc_block_nonce)
        assert_equal(mc_block_previousblockhash, sc_mc_block_previousblockhash)

    def run_test(self):
        # start 1 mc node
        mc_nodes = self.nodes
        print "Number of MC nodes: {0}".format(len(mc_nodes))
        assert_equal(1, len(mc_nodes), "The number of MC nodes is grater than 1.")

        number_of_sidechains = 1
        number_of_accounts_per_sidechain = []
        for i in range(number_of_sidechains):
            number_of_accounts_per_sidechain.append(i+1)

        # Generate information for bootstrapping sidechains
        sc_nodes_info = self.bootstrap_sidechain(number_of_sidechains, number_of_accounts_per_sidechain, mc_nodes[0])
        assert_equal(number_of_sidechains, len(sc_nodes_info), "Not all sidechains have been successfully created.")
        print sc_nodes_info

        # Start sidechain nodes
        sc_nodes = self.sc_setup_nodes(number_of_sidechains)

        # Check validity of genesis information
        for i in range(number_of_sidechains):
            node = sc_nodes[i]
            node_info = sc_nodes_info[i]
            mc_block = mc_nodes[0].getblock(str(node_info[3]))
            # check all keys/boxes/balances are coherent with the default initialization
            self.check_genesis_balances(node, node_info[0], len(node_info[1]), len(node_info[1]), node_info[2]*100000000)
            # verify MC block reference's inclusion
            self.check_mainchan_block_inclusion(node, node_info[0], 1, mc_block, node_info[1])

if __name__ == "__main__":
    SCBootstrap().main()
